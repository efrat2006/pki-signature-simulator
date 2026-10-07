package com.authentisign.desktop.model.dto;


public enum RevocationReason {

    KEY_COMPROMISE("חשד לפגיעה במפתח הפרטי"),
    AFFILIATION_CHANGED("שינוי בפרטי הזהות / ההשתייכות"),
    SUPERSEDED("התעודה הוחלפה בתעודה חדשה"),
    CESSATION_OF_OPERATION("הפסקת השימוש בתעודה"),
    UNSPECIFIED("סיבה אחרת");

    private final String label;

    RevocationReason(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}