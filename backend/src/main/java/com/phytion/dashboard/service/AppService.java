package com.phytion.dashboard.service;

import java.util.List;

import com.phytion.dashboard.config.AppsProperties;
import com.phytion.dashboard.model.App;
import org.springframework.stereotype.Service;

@Service
public class AppService {

    private final List<App> apps;

    public AppService(AppsProperties properties) {
        this.apps = properties.list();
    }

    public List<App> listApps() {
        return apps;
    }

    /**
     * @param appId the name of the app
     * @throws AppNotFoundException when no configured app has that id
     */
    public App getApp(String appId) {
        return apps.stream()
                .filter(app -> app.id().equals(appId))
                .findFirst()
                .orElseThrow(() -> new AppNotFoundException(appId));
    }
}
