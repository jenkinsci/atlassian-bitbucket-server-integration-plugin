package it.com.atlassian.bitbucket.jenkins.internal.util;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;

import java.util.List;
import java.util.function.Supplier;

import static java.util.Optional.empty;
import static java.util.Optional.of;

public final class HtmlUnitUtils {

    private HtmlUnitUtils() {
        // utility class
    }

    public static HtmlDivision getDivByText(HtmlForm htmlForm, String text) throws ElementNotFoundException {
        for (HtmlElement b : htmlForm.getElementsByTagName("div")) {
            if (b instanceof HtmlDivision && b.getTextContent().contains(text)) {
                return (HtmlDivision) b;
            }
        }
        throw new ElementNotFoundException("div", "text", text);
    }

    public static HtmlAnchor getLinkByText(HtmlForm htmlForm, String text) throws ElementNotFoundException {
        for (HtmlElement b : htmlForm.getElementsByTagName("a")) {
            if (b instanceof HtmlAnchor && b.getTextContent().trim().equals(text)) {
                return (HtmlAnchor) b;
            }
        }
        throw new ElementNotFoundException("a", "caption", text);
    }

    public static void waitTillItemIsRendered(Supplier<List<?>> supplier) {
        AsyncTestUtils.waitFor(
                () -> {
                    List serverNames = supplier.get();
                    if (serverNames.isEmpty()) {
                        return of("List has not been rendered");
                    }
                    return empty();
                },
                30000);
    }

}
