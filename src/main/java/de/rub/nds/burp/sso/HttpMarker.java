/**
 * BurpSSOExtension - An extension for BurpSuite that highlights SSO messages.
 * Copyright (C) 2015/ Christian Mainka
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package de.rub.nds.burp.sso;

import burp.IBurpExtenderCallbacks;
import burp.IExtensionHelpers;
import burp.IHttpListener;
import burp.IHttpRequestResponse;
import burp.IParameter;
import burp.IRequestInfo;
import burp.IResponseInfo;
import static de.rub.nds.burp.utilities.ParameterUtilities.getFirstParameterByName;
import static de.rub.nds.burp.utilities.ParameterUtilities.parameterListContainsParameterName;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpMarker implements IHttpListener {

	private String[] OPENID_TOKEN_PARAMETER = {"openid.return_to"};

	private static final Set<String> IN_REQUEST_OPENID2_TOKEN_PARAMETER = new HashSet<String>(Arrays.asList(
		new String[]{"openid.claimed_id", "openid.op_endpoint"}
	));

	private static final Set<String> IN_REQUEST_OAUTH_TOKEN_PARAMETER = new HashSet<String>(Arrays.asList(
		new String[]{"redirect_uri", "scope", "client_id"}
	));

	private static final Set<String> IN_REQUEST_SAML_TOKEN_PARAMETER = new HashSet<String>(Arrays.asList(
		new String[]{"SAMLResponse"}
	));

	private static final Set<String> IN_REQUEST_SAML_REQUEST_PARAMETER = new HashSet<String>(Arrays.asList(
		new String[]{"SAMLRequest"}
	));

	private static final String HIGHLIGHT_COLOR = "yellow";

	private IBurpExtenderCallbacks callbacks;

	private IExtensionHelpers helpers;

	public HttpMarker(IBurpExtenderCallbacks callbacks) {
		this.callbacks = callbacks;
		this.helpers = callbacks.getHelpers();
	}

	@Override
	public void processHttpMessage(int flag, boolean isRequest, IHttpRequestResponse httpRequestResponse) {
		// only flag messages sent/received by the proxy
		if (flag == IBurpExtenderCallbacks.TOOL_PROXY) {
			if (isRequest) {
				processHttpRequest(flag, httpRequestResponse);
			} else {
				processHttpResponse(flag, httpRequestResponse);
			}
		}
	}

	private void processHttpResponse(int flag, IHttpRequestResponse httpRequestResponse) {
//			httpResponse.setComment("Flagged by me with " + flag);
		final byte[] responseBytes = httpRequestResponse.getResponse();
		IResponseInfo responseInfo = helpers.analyzeResponse(responseBytes);
		checkRequestForOpenIdLogin(responseInfo, httpRequestResponse);
	}

	private void processHttpRequest(int flag, IHttpRequestResponse httpRequestResponse) {
		IRequestInfo requestInfo = helpers.analyzeRequest(httpRequestResponse);

		checkRequestForOpenId(requestInfo, httpRequestResponse);
		checkRequestHasOAuthParameters(requestInfo, httpRequestResponse);
		checkRequestForSaml(requestInfo, httpRequestResponse);
		checkRequestForBrowserId(requestInfo, httpRequestResponse);
	}

	private void checkRequestForOpenId(IRequestInfo requestInfo, IHttpRequestResponse httpRequestResponse) {
		final List<IParameter> parameterList = requestInfo.getParameters();
		IParameter openidMode = getFirstParameterByName(parameterList, "openid.mode");
		if (openidMode != null) {
			if (openidMode.getValue().equals("checkid_setup")) {
				markRequestResponse(httpRequestResponse, "OpenID Request");
			} else if (openidMode.getValue().equals("id_res")) {

				if (parameterListContainsParameterName(parameterList, IN_REQUEST_OPENID2_TOKEN_PARAMETER)) {
					markRequestResponse(httpRequestResponse, "OpenID v2 Token");
				} else {
					markRequestResponse(httpRequestResponse, "OpenID v1 Token");
				}
			}
		}
	}

	private void checkRequestHasOAuthParameters(IRequestInfo requestInfo, IHttpRequestResponse httpRequestResponse) {
		if (parameterListContainsParameterName(requestInfo.getParameters(), IN_REQUEST_OAUTH_TOKEN_PARAMETER)) {
			markRequestResponse(httpRequestResponse, "OAuth");
		}
	}

	private void checkRequestForSaml(IRequestInfo requestInfo, IHttpRequestResponse httpRequestResponse) {
		final List<IParameter> parameterList = requestInfo.getParameters();
		if (parameterListContainsParameterName(parameterList, IN_REQUEST_SAML_REQUEST_PARAMETER)) {
			markRequestResponse(httpRequestResponse, "SAML Authentication Request");
		}

		if (parameterListContainsParameterName(parameterList, IN_REQUEST_SAML_TOKEN_PARAMETER)) {
			markRequestResponse(httpRequestResponse, "SAML Token");
		}
	}

	private void checkRequestForOpenIdLogin(IResponseInfo responseInfo, IHttpRequestResponse httpRequestResponse) {
		if (responseInfo.getStatusCode() == STATUS_OK && MIMETYPE_HTML.equals(responseInfo.getStatedMimeType())) {
			final byte[] responseBytes = httpRequestResponse.getResponse();
			final int bodyOffset = responseInfo.getBodyOffset();
			final String responseBody = (new String(responseBytes)).substring(bodyOffset);
			Pattern p = Pattern.compile("=[\"'][^\"']*openid[^\"']*[\"']");
			Matcher m = p.matcher(responseBody);
			if (m.find()) {
				markRequestResponse(httpRequestResponse, "OpenID Login Possibility");
			}
		}
	}

	private void checkRequestForBrowserId(IRequestInfo requestInfo, IHttpRequestResponse httpRequestResponse) {
		final List<IParameter> parameterList = requestInfo.getParameters();
		if (parameterListContainsParameterName(parameterList, "browserid_state")) {
			markRequestResponse(httpRequestResponse, "BrowserId");
		}
	}

	private void markRequestResponse(IHttpRequestResponse httpRequestResponse, String message) {
				httpRequestResponse.setHighlight(HIGHLIGHT_COLOR);
		final String oldComment = httpRequestResponse.getComment();
		if (oldComment != null && !oldComment.isEmpty()) {
			httpRequestResponse.setComment(String.format("%s, %s", oldComment, message));
		} else {
			httpRequestResponse.setComment(message);
		}

	}

	private static final String MIMETYPE_HTML = "HTML";
	private static final int STATUS_OK = 200;
}