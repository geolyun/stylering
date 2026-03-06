package com.stylering.api.dto;

import java.util.List;

public record CreateChatSessionRequest(
        Integer budgetMin,
        Integer budgetMax,
        String fitTop,
        String fitPants,
        String height,
        List<String> occasions
) {}
