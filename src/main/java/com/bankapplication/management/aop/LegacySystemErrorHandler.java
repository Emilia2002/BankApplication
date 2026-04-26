package com.bankapplication.management.aop;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(assignableTypes = {com.bankapplication.management.controllers.SystemDiagnosticsController.class})
public class LegacySystemErrorHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ModelAndView handleMissingMetric(IllegalArgumentException ex) {
        ModelAndView modelAndView = new ModelAndView(new LegacyDiagnosticView());
        modelAndView.addObject("invalidMetricName", ex.getMessage());
        return modelAndView;
    }
}