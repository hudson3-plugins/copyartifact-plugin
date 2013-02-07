/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Alan Harder
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.copyartifact;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.cli.CLI;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.tasks.Builder;
import hudson.util.DescribableList;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import org.apache.commons.httpclient.NameValuePair;
import org.eclipse.hudson.api.model.IBaseBuildableProject;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * Test interaction of BuildSelectorParameter with Jenkins core.
 *
 * @author Alan Harder
 */
public class BuildSelectorParameterTest extends HudsonTestCase {
    
    protected void addBuilder(FreeStyleProject up, Builder builder) throws Exception {
      DescribableList<Builder, Descriptor<Builder>> buildersList = up.getBuildersList();
      buildersList.add(builder);
      up.setBuilders(buildersList);
    }
    
    protected void replaceBuilder(IBaseBuildableProject up, Builder newBuilder) throws IOException  {
      DescribableList<Builder, Descriptor<Builder>> buildersList = up.getBuildersList();
      buildersList.replace(newBuilder);
      up.setBuilders(buildersList);
    }

    /**
     * Verify BuildSelectorParameter works via HTML form, http POST and CLI.
     */
    public void testParameter() throws Exception {
        FreeStyleProject job = createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(
                new BuildSelectorParameter("SELECTOR", new StatusBuildSelector(false), "foo")));
        CaptureEnvironmentBuilder ceb = new CaptureEnvironmentBuilder();
        addBuilder(job, ceb);

        // Run via UI (HTML form)
        WebClient wc = new WebClient();
        // Jenkins sends 405 response for GET of build page.. deal with that:
        wc.setThrowExceptionOnFailingStatusCode(false);
        wc.setPrintContentOnFailingStatusCode(false);
        
        wc.setThrowExceptionOnScriptError(false);
        
        HtmlPage page = wc.getPage(job, "build");
        
        HtmlForm form = page.getFormByName("parameters");
        
        form.getSelectByName("").getOptionByText("Specific build").setSelected(true);
        form.getInputByName("buildNumber").setValueAttribute("6");
        submit(form);
        Queue.Item q = hudson.getQueue().getItem(job);
        if (q != null) {
            q.getFuture().get();
        }
        while (job.getLastBuild().isBuilding()) {
            Thread.sleep(100);
        }
        assertEquals("<hudson.plugins.copyartifact.SpecificBuildSelector><buildNumber>6</buildNumber></hudson.plugins.copyartifact.SpecificBuildSelector>",
                ceb.getEnvVars().get("SELECTOR").replaceAll("\\s+", ""));
        replaceBuilder(job, ceb = new CaptureEnvironmentBuilder());

        // Run via HTTP POST (buildWithParameters)
        WebRequestSettings post = new WebRequestSettings(
                new URL(getURL(), job.getUrl() + "/buildWithParameters"), HttpMethod.POST);
        wc.addCrumb(post);
        String xml = "<hudson.plugins.copyartifact.StatusBuildSelector><stable>true</stable></hudson.plugins.copyartifact.StatusBuildSelector>";
        post.setRequestParameters(Arrays.asList(new NameValuePair("SELECTOR", xml),
                post.getRequestParameters().get(0)));
        wc.getPage(post);
        q = hudson.getQueue().getItem(job);
        if (q != null) {
            q.getFuture().get();
        }
        while (job.getLastBuild().isBuilding()) {
            Thread.sleep(100);
        }
        assertEquals(xml, ceb.getEnvVars().get("SELECTOR"));
        replaceBuilder(job, ceb = new CaptureEnvironmentBuilder());

        // Run via CLI
        CLI cli = new CLI(getURL());
        assertEquals(0, cli.execute(
                "build", job.getFullName(), "-p", "SELECTOR=<hudson.plugins.copyartifact.SavedBuildSelector/>"));
        q = hudson.getQueue().getItem(job);
        if (q != null) {
            q.getFuture().get();
        }
        while (job.getLastBuild().isBuilding()) {
            Thread.sleep(100);
        }
        assertEquals("<hudson.plugins.copyartifact.SavedBuildSelector/>", ceb.getEnvVars().get("SELECTOR"));
    }
}
