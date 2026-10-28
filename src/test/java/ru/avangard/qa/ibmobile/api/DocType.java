package ru.avangard.qa.ibmobile.api;

import java.util.Locale;

/**
 * doc_type документов: значение — имя константы в нижнем регистре.
 * По нему же ищутся шаблон doc_content/&lt;doc_type&gt;.json и схемы schemas/&lt;ручка&gt;/&lt;doc_type&gt;/.
 */
public enum DocType {

    IB_OUR_ACC_TRANSFER,
    IB_FREE_FORMAT_STATEMENT,
    IB_PAY_RUB,
    IB_INSIDE_PAY_RUB,
    IB_CARD_BLOCK_STATEMENT,
    IB_CARD_CHARGEBACK_STATEMENT,
    IB_CARD_ORDER_STATEMENT,
    IB_CARD_REISSUE;

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
