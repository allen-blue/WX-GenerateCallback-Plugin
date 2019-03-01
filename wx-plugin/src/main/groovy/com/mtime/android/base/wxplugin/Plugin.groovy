package com.mtime.android.base.wxplugin

import com.android.build.gradle.AppExtension
import org.gradle.api.Project

class Plugin implements org.gradle.api.Plugin<Project> {

    @Override
    void apply(Project project) {
        def hasPlugin = project.plugins.hasPlugin("com.android.application")
        if (hasPlugin) {
            def android = project.extensions.getByType(AppExtension)
            android.registerTransform(new PluginTransform(project))
        }
    }
}