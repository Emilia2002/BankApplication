package com.bankapplication.management.aop;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

// This class serves as a global exception handler for the SystemDiagnosticsController,
// specifically handling IllegalArgumentExceptions that may arise from invalid metric names.
// It captures the exception, extracts the invalid metric name, and returns
// a ModelAndView object that directs the user to a LegacyDiagnosticView, providing feedback on the error.
@ControllerAdvice(assignableTypes = {com.bankapplication.management.controllers.SystemDiagnosticsController.class})
public class LegacySystemErrorHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ModelAndView handleMissingMetric(IllegalArgumentException ex) {
        ModelAndView modelAndView = new ModelAndView(new LegacyDiagnosticView());
        // Extract the invalid metric name from the exception message and add it to the model for display in the view
        modelAndView.addObject("invalidMetricName", ex.getMessage());
        return modelAndView;
    }
}