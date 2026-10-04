package com.portcelana.natiart.service.support;

/** A safe, field-specific client input error. Neither the field nor message includes the submitted value. */
public final class InputValidationException extends IllegalArgumentException {
    private final String field;

    public InputValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
