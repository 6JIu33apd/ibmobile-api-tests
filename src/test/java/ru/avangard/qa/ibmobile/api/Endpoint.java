package ru.avangard.qa.ibmobile.api;

import io.restassured.http.*;
import ru.avangard.qa.ibmobile.config.Config;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.restassured.http.ContentType.*;

public enum Endpoint {
    AUTH_LOGIN("/Auth/ticket", Method.POST, URLENC),
    AUTH_LOGOUT("/Auth/ticket", Method.DELETE, URLENC),
    SEND_CONFIRM_SMS("/Auth/send_confirm_sms", Method.POST, URLENC),
    CHECK_PASSWORD("/Auth/check_password", Method.POST, URLENC),
    SHOW_SCRETCH("/Auth/show_scretch", Method.POST, URLENC),
    ACC_LIST("/Statement/acc_list", Method.POST, URLENC),
    ACC_DETAILS("/Statement/acc_details", Method.POST, URLENC),
    FULL_HISTORY("/Doc/full_history", Method.POST, URLENC),
    PRIOR_INFO("/Doc/prior_info", Method.POST, URLENC),
    PREPARE_DOC("/Doc/prepare_doc", Method.POST, MULTIPART),
    SEND_DOC("/Doc/send_doc", Method.POST, URLENC);

    private static final Map<String, Properties> VERSIONS = new ConcurrentHashMap<>();
    private final String basePath;
    final Method method;
    final ContentType contentType;

    Endpoint(String path, Method method, ContentType contentType) {
        this.basePath = path;
        this.method = method;
        this.contentType = contentType;
    }

    public String path() { return basePath + version(); }

    public String schema(DocType... docType) {
        return "schemas/" + name().toLowerCase() + (docType.length == 0 ? "" : "/" + docType[0]) + version() + ".json";
    }

    private String version() {
        return VERSIONS.computeIfAbsent(Config.get("release"), release ->
                Config.load(Endpoint.class.getResource("/versions/" + release + ".properties").toString())).getProperty(name());
    }
}
