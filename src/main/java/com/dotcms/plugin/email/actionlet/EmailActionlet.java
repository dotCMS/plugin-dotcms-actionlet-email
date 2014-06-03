package com.dotcms.plugin.email.actionlet;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.internet.InternetAddress;

import com.dotmarketing.beans.Host;
import com.dotmarketing.beans.Identifier;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.Versionable;
import com.dotmarketing.cmis.proxy.DotInvocationHandler;
import com.dotmarketing.cmis.proxy.DotRequestProxy;
import com.dotmarketing.cmis.proxy.DotResponseProxy;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.contentlet.model.ContentletVersionInfo;
import com.dotmarketing.portlets.fileassets.business.FileAsset;
import com.dotmarketing.portlets.workflows.actionlet.WorkFlowActionlet;
import com.dotmarketing.portlets.workflows.model.WorkflowActionClassParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowActionFailureException;
import com.dotmarketing.portlets.workflows.model.WorkflowActionletParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowProcessor;
import com.dotmarketing.util.DNSUtil;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.Mailer;
import com.dotmarketing.util.UtilMethods;
import com.dotmarketing.util.VelocityUtil;

public class EmailActionlet extends WorkFlowActionlet {

	private static final long serialVersionUID = 1L;

	@Override
	public List<WorkflowActionletParameter> getParameters() {
		List<WorkflowActionletParameter> params = new ArrayList<WorkflowActionletParameter>();

		params.add(new WorkflowActionletParameter("fromEmail", "From Email", "", true));
		params.add(new WorkflowActionletParameter("fromName", "From Name", "", true));
		params.add(new WorkflowActionletParameter("toEmail", "To Email", "", true));
		params.add(new WorkflowActionletParameter("toName", "To Name", "", true));
		params.add(new WorkflowActionletParameter("emailSubject", "Email Subject", "", true));
		params.add(new WorkflowActionletParameter("emailBody", "Email Body (html)", "", true));
		params.add(new WorkflowActionletParameter("attachment", "Path to File Attachment or field var for attachment (e.g./images/logo.png or 'fileAsset')", "", false));

		return params;
	}

	@Override
	public String getName() {
		return "Send an Email";
	}

	@Override
	public String getHowTo() {
		return "This actionlet will send an email that can be based on the submitted content. The value of every field here is parsed velocity.  So, to send a custom email to the email address stored in a field called userEmail, put $content.userEmail in the 'to email' field and the system will replace it with the variables from the content";
	}

	@Override
	public void executeAction(WorkflowProcessor processor, Map<String, WorkflowActionClassParameter> params)
			throws WorkflowActionFailureException {

		Contentlet c = processor.getContentlet();

		String rev = c.getStringProperty("ipAddress");
		try {
			rev = DNSUtil.reverseDns(rev);
			c.setStringProperty("ipAddress", rev);
		} catch (Exception e) {
			Logger.error(this.getClass(), "error on reverse lookup" + e.getMessage());
		}

		String toEmail = params.get("toEmail").getValue();
		String toName = params.get("toName").getValue();
		String fromEmail = params.get("fromEmail").getValue();
		String fromName = params.get("fromName").getValue();
		String emailSubject = params.get("emailSubject").getValue();
		String emailBody = params.get("emailBody").getValue();
		String attachment = params.get("attachment").getValue();
		
		
		

		try {
			// get the host of the content
			Host host = APILocator.getHostAPI().find(processor.getContentlet().getHost(),
					APILocator.getUserAPI().getSystemUser(), false);
			if (host.isSystemHost()) {
				host = APILocator.getHostAPI().findDefaultHost(APILocator.getUserAPI().getSystemUser(), false);
			}

			InvocationHandler dotInvocationHandler = new DotInvocationHandler(new HashMap());

			DotRequestProxy requestProxy = (DotRequestProxy) Proxy.newProxyInstance(DotRequestProxy.class.getClassLoader(),new Class[] { DotRequestProxy.class }, dotInvocationHandler);
			requestProxy.put("host", host);
			requestProxy.put("host_id", host.getIdentifier());
			requestProxy.put("user", processor.getUser());
			DotResponseProxy responseProxy = (DotResponseProxy) Proxy.newProxyInstance(DotResponseProxy.class.getClassLoader(), new Class[] { DotResponseProxy.class }, dotInvocationHandler);

			org.apache.velocity.context.Context ctx = VelocityUtil.getWebContext(requestProxy, responseProxy);
			ctx.put("host", host);
			ctx.put("host_id", host.getIdentifier());
			ctx.put("user", processor.getUser());
			ctx.put("workflow", processor);
			ctx.put("stepName", processor.getStep().getName());
			ctx.put("stepId", processor.getStep().getId());
			ctx.put("nextAssign", processor.getNextAssign().getName());
			ctx.put("workflowMessage", processor.getWorkflowMessage());
			ctx.put("nextStepResolved", processor.getNextStep().isResolved());
			ctx.put("nextStepId", processor.getNextStep().getId());
			ctx.put("nextStepName", processor.getNextStep().getName());
			ctx.put("workflowTaskTitle", UtilMethods.isSet(processor.getTask().getTitle()) ? processor.getTask().getTitle() : processor.getContentlet().getTitle());
			ctx.put("modDate", processor.getTask().getModDate());
			ctx.put("structureName", processor.getContentlet().getStructure().getName());

			ctx.put("contentlet", c);
			ctx.put("content", c);

			if(UtilMethods.isSet(toEmail)){
				toEmail = VelocityUtil.eval(toEmail, ctx);
			}
			if(UtilMethods.isSet(toName)){
				toName = VelocityUtil.eval(toName, ctx);
			}
			if(UtilMethods.isSet(fromEmail)){
				fromEmail = VelocityUtil.eval(fromEmail, ctx);
			}

			if(UtilMethods.isSet(fromName)){
				fromName = VelocityUtil.eval(fromName, ctx);
			}
			if(UtilMethods.isSet(emailSubject)){
				emailSubject = VelocityUtil.eval(emailSubject, ctx);
			}
			if(UtilMethods.isSet(emailBody)){
				emailBody = VelocityUtil.eval(emailBody, ctx);
			}

			Mailer mail = new Mailer();

			InternetAddress to = new InternetAddress(toName + "<" + toEmail + ">");
			mail.setToEmail(to.toString());

			mail.setFromEmail(fromEmail);
			mail.setFromName(fromName);
			mail.setSubject(emailSubject);

			mail.setHTMLAndTextBody(emailBody);
			if (UtilMethods.isSet(attachment)) {
				File f = null;
				try {
					if(attachment.indexOf("/") == -1 && processor.getContentlet().getBinary(attachment).exists()){
						f = processor.getContentlet().getBinary(attachment);
					}
					else{
						Identifier id = APILocator.getIdentifierAPI().find(host, attachment);
						ContentletVersionInfo vinfo = APILocator.getVersionableAPI().getContentletVersionInfo(id.getId(),processor.getContentlet().getLanguageId());
						Contentlet cont = APILocator.getContentletAPI().find(vinfo.getLiveInode(), processor.getUser(), true);
						FileAsset fileAsset = APILocator.getFileAssetAPI().fromContentlet(cont);
						f = fileAsset.getFileAsset();
					}
					if(f!=null && f.exists()){
						mail.addAttachment(f);
					}
				} catch (Exception e) {
					Logger.error(this.getClass(), "Unable to get file attachment: " + e.getMessage());
				}
			}

			mail.sendMessage();

		} catch (Exception e) {
			Logger.error(EmailActionlet.class, e.getMessage(), e);
		}

	}

}
