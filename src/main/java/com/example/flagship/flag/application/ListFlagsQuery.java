package com.example.flagship.flag.application;

public record ListFlagsQuery(FlagStatusFilter status, String text, int page, int size) {}
