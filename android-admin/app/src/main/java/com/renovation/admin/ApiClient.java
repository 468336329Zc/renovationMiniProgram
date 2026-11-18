package com.renovation.admin;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 轻量 HTTP 客户端：管理端只调用 Flask API，不直连 MySQL。 */
final class ApiClient {
    static final String DEFAULT_BASE_URL = "http://10.0.2.2:5000/api";
    private static final int TIMEOUT = 8000;

    private ApiClient() {}

    static String normalizeBase(String base) {
        String value = base == null ? DEFAULT_BASE_URL : base.trim();
        if (value.isEmpty()) value = DEFAULT_BASE_URL;
        if (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    static JSONObject request(String base, String path, String method, JSONObject body, String token) throws ApiException {
        HttpURLConnection connection = null;
        try {
            String url = normalizeBase(base) + path;
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-Device-Label", "Android 管理端");
            if (token != null && !token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String raw = read(stream);
            JSONObject response = raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
            boolean ok = response.optBoolean("ok", false);
            if (!ok) {
                JSONObject error = response.optJSONObject("error");
                String message = error == null ? "请求失败" : error.optString("message", "请求失败");
                String code = error == null ? "http_error" : error.optString("code", "http_error");
                throw new ApiException(status, code, message);
            }
            JSONObject data = response.optJSONObject("data");
            return data == null ? new JSONObject() : data;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(0, "network_error", "无法连接服务端：" + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    static class ApiException extends Exception {
        final int status;
        final String code;

        ApiException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
