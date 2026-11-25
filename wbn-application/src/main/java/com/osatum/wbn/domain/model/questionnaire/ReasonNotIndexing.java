package com.osatum.wbn.domain.model.questionnaire;

public enum ReasonNotIndexing {


    dec01(false),
    dec02(false),
    dec03(false),
    dec04(false),
    dec05(false),
    dec06(true),
    dec07(true),
    dec08(false),
    dec09(false),
    dec10(false),
    dec11(false),
    dec12(false);


    private boolean isQuestionarieVisible;


    ReasonNotIndexing(boolean isQuestionarieVisible) {
        this.isQuestionarieVisible = isQuestionarieVisible;
    }

    public boolean isQuestionarieVisible() {
        return isQuestionarieVisible;
    }
}
