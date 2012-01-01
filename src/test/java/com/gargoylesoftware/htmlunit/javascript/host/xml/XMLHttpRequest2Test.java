/*
 * Copyright (c) 2002-2012 Gargoyle Software Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gargoylesoftware.htmlunit.javascript.host.xml;

import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.gargoylesoftware.htmlunit.BrowserRunner;
import com.gargoylesoftware.htmlunit.BrowserRunner.Alerts;
import com.gargoylesoftware.htmlunit.BrowserRunner.NotYetImplemented;
import com.gargoylesoftware.htmlunit.WebDriverTestCase;
import com.gargoylesoftware.htmlunit.WebRequest;

/**
 * Additional tests for {@link XMLHttpRequest} using already WebDriverTestCase.
 *
 * @version $Revision$
 * @author Marc Guillemot
 * @author Ahmed Ashour
 */
@RunWith(BrowserRunner.class)
public class XMLHttpRequest2Test extends WebDriverTestCase {
    private static String XHRInstantiation_ = "(window.XMLHttpRequest ? "
        + "new XMLHttpRequest() : new ActiveXObject('Microsoft.XMLHTTP'))";

    /**
     * This produced a deadlock situation with HtmlUnit-2.6 and HttmlUnit-2.7-SNAPSHOT on 17.09.09.
     * The reason is that HtmlUnit has currently one "JS execution thread" per window, synchronizing on the
     * owning page BUT the XHR callback execution are synchronized on their "owning" page.
     * This test isn't really executed now to avoid the deadlock.
     * Strangely, this test seem to fail even without the implementation of the "/setStateXX" handling
     * on the "server side".
     * Strange thing.
     * @throws Exception if the test fails
     */
    @Test
    @NotYetImplemented
    public void deadlock() throws Exception {
        final String jsCallSynchXHR = "function callSynchXHR(url) {\n"
            + "  var xhr = " + XHRInstantiation_ + ";\n"
            + "  xhr.open('GET', url, false);\n"
            + "  xhr.send('');\n"
            + "}\n";
        final String jsCallASynchXHR = "function callASynchXHR(url) {\n"
            + "  var xhr = " + XHRInstantiation_ + ";\n"
            + "  var handler = function() {\n"
            + "    if (xhr.readyState == 4)\n"
            + "      alert(xhr.responseText);\n"
            + "  }\n"
            + "  xhr.onreadystatechange = handler;\n"
            + "  xhr.open('GET', url, true);\n"
            + "  xhr.send('');\n"
            + "}\n";
        final String html = "<html><head><script>\n"
            + jsCallSynchXHR
            + jsCallASynchXHR
            + "function testMain() {\n"
            + "  // set state 1 and wait for state 2\n"
            + "  callSynchXHR('/setState1/setState3')\n"
            + "  // call function with XHR and handler in frame\n"
            + "  myFrame.contentWindow.callASynchXHR('/fooCalledFromFrameCalledFromMain');\n"
            + "}\n"
            + "</script></head>\n"
            + "<body onload='testMain()'>\n"
            + "<iframe id='myFrame' src='frame.html'></iframe>\n"
            + "</body></html>";

        final String frame = "<html><head><script>\n"
            + jsCallSynchXHR
            + jsCallASynchXHR
            + "function testFrame() {\n"
            + "  // set state 2\n"
            + "  callSynchXHR('/setState2')\n"
            + "  // call function with XHR and handler in parent\n"
            + "  parent.callASynchXHR('/fooCalledFromMainCalledFromFrame');\n"
            + "}\n"
            + "setTimeout(testFrame, 10);\n"
            + "</script></head>\n"
            + "<body></body></html>";

        getMockWebConnection().setResponse(new URL(getDefaultUrl(), "frame.html"), frame);
        getMockWebConnection().setDefaultResponse(""); // for all XHR

        // just to avoid unused variable warning when the next line is commented
        getMockWebConnection().setResponse(getDefaultUrl(), html);
        // loadPageWithAlerts2(html);
        Assert.fail("didn't run the real test as it causes a deadlock");
    }

    /**
     * @throws Exception if an error occurs
     */
    @Test
    public void setRequestHeader() throws Exception {
        final String html = "<html><head><script>\n"
            + "  function test() {\n"
            + "    var xhr = " + XHRInstantiation_ + ";\n"
            + "    xhr.open('GET', 'second.html', false);\n"
            + "    xhr.setRequestHeader('Accept', 'text/javascript, application/javascript, */*');\n"
            + "    xhr.setRequestHeader('Accept-Language', 'ar-eg');\n"
            + "    xhr.send('');\n"
            + "  }\n"
            + "</script></head><body onload='test()'></body></html>";

        getMockWebConnection().setDefaultResponse("");
        loadPage2(html);

        final WebRequest lastRequest = getMockWebConnection().getLastWebRequest();
        final Map<String, String> headers = lastRequest.getAdditionalHeaders();
        assertEquals("text/javascript, application/javascript, */*", headers.get("Accept"));
        assertEquals("ar-eg", headers.get("Accept-Language"));
    }

    /**
     * Content-Length header is simply ignored by browsers as it
     * is the browser's responsibility to set it.
     * @throws Exception if an error occurs
     */
    @Test
    public void requestHeader_contentLength() throws Exception {
        requestHeader_contentLength("1234");
        requestHeader_contentLength("11");
        requestHeader_contentLength(null);
    }

    private void requestHeader_contentLength(final String headerValue) throws Exception {
        final String body = "hello world";
        final String setHeader = headerValue == null ? ""
                : "xhr.setRequestHeader('Content-length', 1234);\n";
        final String html = "<html><body><script>\n"
            + "var xhr = " + XHRInstantiation_ + ";\n"
            + "xhr.open('POST', 'second.html', false);\n"
            + "var body = '" + body + "';\n"
            + "xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');\n"
            + setHeader
            + "xhr.send(body);\n"
            + "</script></body></html>";

        getMockWebConnection().setDefaultResponse("");
        loadPage2(html);

        final WebRequest lastRequest = getMockWebConnection().getLastWebRequest();
        final Map<String, String> headers = lastRequest.getAdditionalHeaders();
        assertEquals("" + body.length(), headers.get("Content-Length"));
    }

    /**
     * XHR.open throws an exception if URL parameter is null or empty string.
     * @throws Exception if an error occurs
     */
    @Test
    @Alerts({ "exception", "exception", "pass", "pass" })
    public void openThrowOnEmptyUrl() throws Exception {
        final String html = "<html><head>\n"
            + "<script>\n"
            + "var xhr = " + XHRInstantiation_ + ";\n"
            + "var values = [null, '', ' ', '  \\t  '];\n"
            + "for (var i=0; i<values.length; ++i) {\n"
            + "  try {\n"
            + "    xhr.open('GET', values[i], false);\n"
            + "    xhr.send('');\n"
            + "    alert('pass');\n"
            + "  } catch(e) { alert('exception') }\n"
            + "}\n"
            + "</script>\n"
            + "</head>\n"
            + "<body></body>\n</html>";

        loadPageWithAlerts2(html);
        assertEquals(3, getMockWebConnection().getRequestCount());
    }

    /**
     * Test access to the XML DOM.
     * @throws Exception if the test fails
     */
    @Test
    @Alerts({ "1", "bla", "someAttr", "someValue", "true", "foo", "2", "fi1" })
    public void responseXML() throws Exception {
        testResponseXML("text/xml");
        testResponseXML(null);
    }

    /**
     * Test access to responseXML when the content type indicates that it is not XML.
     * @throws Exception if the test fails
     */
    @Test
    @Alerts("null")
    public void responseXML_badContentType() throws Exception {
        final String html = "<html><head>\n"
            + "<script>\n"
            + "function test() {\n"
            + "  var xhr = " + XHRInstantiation_ + ";\n"
            + "  xhr.open('GET', 'foo.xml', false);\n"
            + "  xhr.send('');\n"
            + "  alert(xhr.responseXML);\n"
            + "}\n"
            + "</script>\n"
            + "</head>\n"
            + "<body onload='test()'></body></html>";

        final URL urlFoo = new URL(URL_FIRST + "foo.xml");
        getMockWebConnection().setResponse(urlFoo, "<bla someAttr='someValue'><foo><fi id='fi1'/><fi/></foo></bla>\n",
            "text/plain");
        loadPageWithAlerts2(html);
    }

    private void testResponseXML(final String contentType) throws Exception {
        final String html = "<html><head>\n"
            + "<script>\n"
            + "function test() {\n"
            + "  var xhr = " + XHRInstantiation_ + ";\n"
            + "  xhr.open('GET', 'foo.xml', false);\n"
            + "  xhr.send('');\n"
            + "  var childNodes = xhr.responseXML.childNodes;\n"
            + "  alert(childNodes.length);\n"
            + "  var rootNode = childNodes[0];\n"
            + "  alert(rootNode.nodeName);\n"
            + "  alert(rootNode.attributes[0].nodeName);\n"
            + "  alert(rootNode.attributes[0].nodeValue);\n"
            + "  alert(rootNode.attributes['someAttr'] == rootNode.attributes[0]);\n"
            + "  alert(rootNode.firstChild.nodeName);\n"
            + "  alert(rootNode.firstChild.childNodes.length);\n"
            + "  alert(xhr.responseXML.getElementsByTagName('fi').item(0).attributes[0].nodeValue);\n"
            + "}\n"
            + "</script>\n"
            + "</head>\n"
            + "<body onload='test()'></body></html>";

        final URL urlFoo = new URL(URL_FIRST + "foo.xml");
        getMockWebConnection().setResponse(urlFoo, "<bla someAttr='someValue'><foo><fi id='fi1'/><fi/></foo></bla>\n",
            contentType);
        loadPageWithAlerts2(html);
    }

    /**
     * Test access to responseXML when the content type indicates that it is not XML.
     * @throws Exception if the test fails
     */
    @Test
    @Alerts("null")
    public void responseXML_sendNotCalled() throws Exception {
        final String html = "<html><head>\n"
            + "<script>\n"
            + "function test() {\n"
            + "  var xhr = " + XHRInstantiation_ + ";\n"
            + "  xhr.open('GET', 'foo.xml', false);\n"
            + "  alert(xhr.responseXML);\n"
            + "}\n"
            + "</script>\n"
            + "</head>\n"
            + "<body onload='test()'></body></html>";

        final URL urlFoo = new URL(URL_FIRST + "foo.xml");
        getMockWebConnection().setResponse(urlFoo, "<bla someAttr='someValue'><foo><fi id='fi1'/><fi/></foo></bla>\n",
            "text/plain");
        loadPageWithAlerts2(html);
    }

    /**
     * Test for Bug 2891430: HtmlUnit should not violate the same-origin policy with FF3.
     * Note: FF3.5 doesn't enforce this same-origin policy.
     * @throws Exception if the test fails
     */
    @Test
    @Alerts("exception")
    public void sameOriginPolicy() throws Exception {
        sameOriginPolicy(URL_THIRD.toString());
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts(FF = "exception", IE = "ok")
    public void sameOriginPolicy_aboutBlank() throws Exception {
        sameOriginPolicy("about:blank");
    }

    private void sameOriginPolicy(final String url) throws Exception {
        final String html = "<html><head>\n"
            + "<script>\n"
            + "function test() {\n"
            + "  var xhr = " + XHRInstantiation_ + ";\n"
            + "  try {\n"
            + "    xhr.open('GET', '" + url + "', false);\n"
            + "    alert('ok');\n"
            + "  } catch(e) { alert('exception'); }\n"
            + "}\n"
            + "</script>\n"
            + "</head>\n"
            + "<body onload='test()'></body></html>";

        getMockWebConnection().setResponse(URL_THIRD, "<bla/>", "text/xml");
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if an error occurs
     */
    @Test
    public void put() throws Exception {
        final String html = "<html><head><script>\n"
            + "  function test() {\n"
            + "    var xhr = " + XHRInstantiation_ + ";\n"
            + "    xhr.open('PUT', 'second.html', false);\n"
            + "    xhr.send('Something');\n"
            + "  }\n"
            + "</script></head><body onload='test()'></body></html>";

        getMockWebConnection().setDefaultResponse("");
        loadPage2(html);

        final String requestBody = getMockWebConnection().getLastWebRequest().getRequestBody();
        assertEquals("Something", requestBody);
    }

    /**
     * Regression test for bug 2952333.
     * This test was causing a java.lang.ClassCastException:
     * com.gargoylesoftware.htmlunit.xml.XmlPage cannot be cast to com.gargoylesoftware.htmlunit.html.HtmlPage
     * @throws Exception if an error occurs
     */
    @Test
    @Alerts(FF = "[object XMLDocument]", IE = "[object]")
    public void iframeInResponse() throws Exception {
        final String html = "<html><head><script>\n"
            + "var xhr = " + XHRInstantiation_ + ";\n"
            + "xhr.open('GET', 'foo.xml', false);\n"
            + "xhr.send();\n"
            + "alert(xhr.responseXML);\n"
            + "</script></head><body></body></html>";

        final String xml = "<html xmlns='http://www.w3.org/1999/xhtml'>\n"
            + "<body><iframe></iframe></body></html>";
        getMockWebConnection().setDefaultResponse(xml, "text/xml");
        loadPageWithAlerts2(html);
    }

    /**
     * Ensures that XHR download is performed without altering other JS jobs.
     * Currently HtmlUnit doesn't behave correctly here because download and callback execution
     * are executed within the same synchronize block on the HtmlPage.
     * @throws Exception if an error occurs
     */
    @Test
    @NotYetImplemented
    @Alerts({ "in timeout", "hello" })
    public void xhrDownloadInBackground() throws Exception {
        final String html = "<html><head><script>\n"
            + "var xhr = " + XHRInstantiation_ + ";\n"
            + "var handler = function() {\n"
            + "  if (xhr.readyState == 4)\n"
            + "    alert(xhr.responseText);\n"
            + "}\n"
            + "xhr.onreadystatechange = handler;\n"
            + "xhr.open('GET', '/delay200/foo.txt', true);\n"
            + "xhr.send('');\n"
            + "setTimeout(function(){ alert('in timeout');}, 5);\n"
            + "</script></head><body></body></html>";

        getMockWebConnection().setDefaultResponse("hello", "text/plain");
        loadPageWithAlerts2(html);
    }

    /**
     * Ensures that XHR callback is executed before a timeout, even if it is time
     * to execute this one.
     * @throws Exception if an error occurs
     */
    @Test
    @Alerts({ "hello", "in timeout" })
    public void xhrCallbackBeforeTimeout() throws Exception {
        final String html = "<html><head><script>\n"
            + "function wait() {\n"
            + "  var xhr = " + XHRInstantiation_ + ";\n"
            + "  xhr.open('GET', '/delay200/foo.txt', false);\n"
            + "  xhr.send('');\n"
            + "}\n"
            + "function doTest() {\n"
            + "  setTimeout(function(){ alert('in timeout');}, 5);\n"
            + "  wait();\n"
            + "  var xhr2 = " + XHRInstantiation_ + ";\n"
            + "  var handler = function() {\n"
            + "    if (xhr2.readyState == 4)\n"
            + "      alert(xhr2.responseText);\n"
            + "  }\n"
            + "  xhr2.onreadystatechange = handler;\n"
            + "  xhr2.open('GET', '/foo.txt', true);\n"
            + "  xhr2.send('');\n"
            + "  wait();\n"
            + "}\n"
            + "setTimeout(doTest, 10);\n"
            + "</script></head><body></body></html>";

        getMockWebConnection().setDefaultResponse("hello", "text/plain");
        loadPageWithAlerts2(html, 2000);
    }

    /**
     * @throws Exception if an error occurs
     */
    @Test
    @Alerts("a=b,0")
    public void post() throws Exception {
        final String html = "<html><head><script>\n"
            + "function test() {\n"
            + "  var xhr = " + XHRInstantiation_ + ";\n"
            + "  xhr.open('POST', '/test2?a=b', false);\n"
            + "  xhr.send('');\n"
            + "  alert(xhr.responseText);\n"
            + "}\n"
            + "</script></head><body onload='test()'></body></html>";

        final Map<String, Class<? extends Servlet>> servlets = new HashMap<String, Class<? extends Servlet>>();
        servlets.put("/test2", PostServlet2.class);

        loadPageWithAlerts2(html, servlets);
    }

    /**
     * Servlet for {@link #post()}.
     */
    public static class PostServlet2 extends HttpServlet {

        @Override
        protected void doPost(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
            final Writer writer = resp.getWriter();
            writer.write(req.getQueryString() + ',' + req.getContentLength());
            writer.close();
        }
    }

    /**
     * Firefox up to 3.6 does not call onreadystatechange handler if sync.
     * @throws Exception if the test fails
     */
    @Test
    @Alerts(IE = { "1", "2", "3", "4" })
    public void testOnreadystatechange_sync() throws Exception {
        final String html =
              "<html>\n"
            + "  <head>\n"
            + "    <title>XMLHttpRequest Test</title>\n"
            + "    <script>\n"
            + "      var request;\n"
            + "      function test() {\n"
            + "        if (window.XMLHttpRequest)\n"
            + "          request = new XMLHttpRequest();\n"
            + "        else if (window.ActiveXObject)\n"
            + "          request = new ActiveXObject('Microsoft.XMLHTTP');\n"
            + "        request.open('GET', '" + URL_SECOND + "', false);\n"
            + "        request.onreadystatechange = onStateChange;\n"
            + "        request.send('');\n"
            + "      }\n"
            + "      function onStateChange() {\n"
            + "        alert(request.readyState);\n"
            + "      }\n"
            + "    </script>\n"
            + "  </head>\n"
            + "  <body onload='test()'>\n"
            + "  </body>\n"
            + "</html>";

        final String xml =
              "<xml>\n"
            + "<content>blah</content>\n"
            + "<content>blah2</content>\n"
            + "</xml>";

        getMockWebConnection().setResponse(URL_SECOND, xml, "text/xml");
        loadPageWithAlerts2(html);
    }
}
