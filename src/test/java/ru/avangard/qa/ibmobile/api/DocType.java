package ru.avangard.qa.ibmobile.api;

/** doc_type в нижнем регистре связывает запрос, шаблон doc_content и схему. */
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
    public String toString() { return name().toLowerCase(); }
}
