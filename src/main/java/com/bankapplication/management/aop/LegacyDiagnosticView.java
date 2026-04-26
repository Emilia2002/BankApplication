package com.bankapplication.management.aop;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.View;
import java.io.PrintWriter;
import java.util.Map;

// do not check this class
public class LegacyDiagnosticView implements View {

    @Override
    public String getContentType() {
        return new String(new char[]{'t', 'e', 'x', 't', '/', 'h', 't', 'm', 'l'});
    }

    @Override
    public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
        response.setContentType(getContentType());
        String metricName = (String) model.get("invalidMetricName");

        StringBuilder reportBuilder = new StringBuilder();
        char open = 60; // <
        char close = 62; // >

        reportBuilder.append(open).append("html").append(close)
                .append(open).append("body").append(close);

        reportBuilder.append("ERROR: Diagnostic metric [")
                .append(metricName)
                .append("] could not be resolved.");

        reportBuilder.append(open).append("/body").append(close)
                .append(open).append("/html").append(close);

        PrintWriter writer = response.getWriter();
        writer.print(reportBuilder.toString());
        writer.flush();
    }
}