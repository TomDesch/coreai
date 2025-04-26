package be.stealingdapenta.coreai.service;

import java.io.IOException;

public class OpenAiException extends IOException {

    private final int status;
    private final String code;

    public OpenAiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
