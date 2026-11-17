package ru.avangard.qa.ibmobile.api;

import io.restassured.http.Method;
import ru.avangard.qa.ibmobile.config.Config;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Пути и схемы; версии ручек выбираются из файла текущего релиза. */
public enum Endpoint {
    AUTH_LOGIN("Auth/ticket", Method.POST),
    AUTH_LOGOUT("Auth/ticket", Method.DELETE),
    SEND_CONFIRM_SMS("Auth/send_confirm_sms", Method.POST),
    CHECK_PASSWORD("Auth/check_password", Method.POST),
    SHOW_SCRETCH("Auth/show_scretch", Method.POST),
    ACC_LIST("Statement/acc_list", Method.POST),
    ACC_DETAILS("Statement/acc_details", Method.POST),
    FULL_HISTORY("Doc/full_history", Method.POST),
    PRIOR_INFO("Doc/prior_info", Method.POST),
    PREPARE_DOC("Doc/prepare_doc", Method.POST),
    SEND_DOC("Doc/send_doc", Method.POST);

    private static final Map<String, Properties> VERSIONS = new ConcurrentHashMap<>();
    public final String basePath;
    public final Method method;

    Endpoint(String path, Method method) { this.basePath = path; this.method = method; }

    public String path() { return basePath + version(); }

    public String schema(DocType... docType) {
        return "schemas/" + name().toLowerCase() + (docType.length == 0 ? "" : "/" + docType[0]) + version() + ".json";
    }

    private String version() {
        return VERSIONS.computeIfAbsent(Config.get("release"), release ->
                Config.load(Endpoint.class.getResource("/versions/" + release + ".properties").toString())).getProperty(name());
    }
}
