
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.gui;

import com.vaadin.ui.Component;

import org.dellroad.stuff.vaadin7.VaadinConfigurable;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Main GUI {@link com.vaadin.ui.UI} component.
 */
@SuppressWarnings("serial")
@VaadinConfigurable
public class MainUI extends AbstractUI {

    public static final String URI_PATH = "main";

    @Autowired
    private GUIConfig guiConfig;

    @Override
    protected String getTitle() {
        return "JSimpleDB Viewer";
    }

    @Override
    protected Component getTopRightLabel() {
        return new SizedLabel(this.guiConfig.getDatabaseDescription());
    }

    @Override
    protected Component buildMainPanel() {
        return new MainPanel(this.guiConfig);
    }
}

