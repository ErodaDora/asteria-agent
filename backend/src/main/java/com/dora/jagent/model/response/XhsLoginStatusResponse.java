package com.dora.jagent.model.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class XhsLoginStatusResponse {

    private boolean loggedIn;

    private boolean loginWindowRunning;

    private String storageStatePath;

    private String message;
}
