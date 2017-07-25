package com.symphony.adminbot.api;

import com.symphony.adminbot.api.impl.AbstractV1AdminService;
import com.symphony.adminbot.bootstrap.service.DeveloperBootstrapService;
import com.symphony.adminbot.commons.BotConstants;
import com.symphony.adminbot.model.session.AdminBotSession;
import com.symphony.adminbot.model.session.AdminBotUserSession;
import com.symphony.adminbot.model.session.AdminBotUserSessionManager;
import com.symphony.api.adminbot.model.Developer;
import com.symphony.api.adminbot.model.DeveloperBootstrapInfo;
import com.symphony.api.adminbot.model.DeveloperSignUpForm;
import com.symphony.api.pod.client.ApiException;

import io.swagger.annotations.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.core.Response;


/**
 * Created by nick.tarsillo on 7/5/17.
 */
public class V1AdminApi extends AbstractV1AdminService {
  private static final Logger LOG = LoggerFactory.getLogger(V1AdminApi.class);

  private AdminBotUserSessionManager adminSessionManager;
  private AdminBotSession adminBotSession;

  public V1AdminApi(AdminBotUserSessionManager adminSessionManager, AdminBotSession adminBotSession){
    this.adminSessionManager = adminSessionManager;
    this.adminBotSession = adminBotSession;
  }

  @Override
  public DeveloperBootstrapInfo bootstrapDeveloper(Developer developer) {
    DeveloperBootstrapService signUpService = adminBotSession.getBootstrapService();
    try {
      DeveloperBootstrapInfo developerBootstrapInfo = signUpService.bootstrapDeveloper(developer);
      return developerBootstrapInfo;
    } catch (ApiException e) {
      LOG.error("Bootstrap partner welcome failed:", e);
      throw new InternalServerErrorException(BotConstants.INTERNAL_ERROR);
    }
  }

  @Override
  public DeveloperBootstrapInfo bootstrapDevelopers(DeveloperSignUpForm signUpForm) {
    DeveloperBootstrapService signUpService = adminBotSession.getBootstrapService();
    try {
      DeveloperBootstrapInfo developerBootstrapInfo = signUpService.bootstrapDevelopers(signUpForm);
      return developerBootstrapInfo;
    } catch (ApiException e) {
      LOG.error("Bootstrap partner welcome failed:", e);
      throw new InternalServerErrorException(BotConstants.INTERNAL_ERROR);
    }
  }

  @Override
  public String sendDeveloperWelcome(DeveloperSignUpForm signUpForm) {
    DeveloperBootstrapService signUpService = adminBotSession.getBootstrapService();
    try {
      signUpService.welcomeDeveloper(signUpForm);
    } catch (ApiException e) {
      LOG.error("Send partner welcome failed:", e);
      throw new InternalServerErrorException(BotConstants.INTERNAL_ERROR);
    }

    return BotConstants.DEVELOPER_WELCOME_SUCCESS;
  }

  @Override
  public AdminBotUserSession getAdminUserSession(String sessionToken) {
    AdminBotUserSession adminSession = adminSessionManager.getAdminSession(sessionToken);
    if(adminSession == null) {
      throw new BadRequestException("Admin session not found.");
    }

    return adminSession;
  }
}
