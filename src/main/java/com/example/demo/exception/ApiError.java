package com.example.demo.exception;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class ApiError {

    private Instant timestamp;
    private int status;
    private String code;
    private String message;
}