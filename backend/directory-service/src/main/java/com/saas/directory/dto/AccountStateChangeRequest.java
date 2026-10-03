package com.saas.directory.dto;

import com.saas.directory.model.RoleName;

public record AccountStateChangeRequest(Boolean active, RoleName role) {}
