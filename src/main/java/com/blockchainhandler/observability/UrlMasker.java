package com.blockchainhandler.observability;

import java.net.URI;

/**
 * Hides credentials in node URLs before they are logged or exposed through actuator.
 *
 * <p>RPC providers usually put the API key into the path or the query string, so only the scheme, host and
 * port are kept.
 */
public final class UrlMasker {

    private UrlMasker() {
    }

    /**
     * Masks everything after the authority of the URL.
     *
     * @param url URL to mask, may be {@code null}
     * @return URL that is safe to log
     */
    public static String mask(String url) {
        if (url == null) {
            return "n/a";
        }
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null) {
                return "***";
            }
            String authority = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            boolean hasSecretParts = uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || (uri.getRawPath() != null && uri.getRawPath().length() > 1);
            return hasSecretParts ? authority + "/***" : authority;
        } catch (IllegalArgumentException e) {
            return "***";
        }
    }
}
