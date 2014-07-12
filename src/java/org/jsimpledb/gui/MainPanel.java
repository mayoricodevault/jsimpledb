
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.gui;

import com.google.common.reflect.TypeToken;
import com.vaadin.data.Property;
import com.vaadin.data.util.MethodProperty;
import com.vaadin.server.Sizeable;
import com.vaadin.server.VaadinSession;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.ui.AbstractField;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Component;
import com.vaadin.ui.DefaultFieldFactory;
import com.vaadin.ui.Field;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.HorizontalSplitPanel;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.validation.constraints.NotNull;

import org.dellroad.stuff.spring.RetryTransaction;
import org.dellroad.stuff.vaadin7.EnumComboBox;
import org.dellroad.stuff.vaadin7.FieldBuilder;
import org.dellroad.stuff.vaadin7.VaadinConfigurable;
import org.dellroad.stuff.vaadin7.VaadinUtil;
import org.jsimpledb.JClass;
import org.jsimpledb.JField;
import org.jsimpledb.JObject;
import org.jsimpledb.JReferenceField;
import org.jsimpledb.JSimpleDB;
import org.jsimpledb.JSimpleField;
import org.jsimpledb.JTransaction;
import org.jsimpledb.change.ObjectCreate;
import org.jsimpledb.change.ObjectDelete;
import org.jsimpledb.core.DeletedObjectException;
import org.jsimpledb.core.FieldType;
import org.jsimpledb.core.ObjId;
import org.jsimpledb.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

/**
 * Main GUI panel containing the various tabs.
 */
@SuppressWarnings("serial")
@VaadinConfigurable(preConstruction = true)
public class MainPanel extends VerticalLayout {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    // Buttons
    private final Button editButton = new Button("Edit", new Button.ClickListener() {
        @Override
        public void buttonClick(Button.ClickEvent event) {
            MainPanel.this.editButtonClicked((ObjId)MainPanel.this.objectTable.getValue());
        }
    });
    private final Button newButton = new Button("New", new Button.ClickListener() {
        @Override
        public void buttonClick(Button.ClickEvent event) {
            MainPanel.this.newButtonClicked();
        }
    });
    private final Button deleteButton = new Button("Delete", new Button.ClickListener() {
        @Override
        public void buttonClick(Button.ClickEvent event) {
            MainPanel.this.deleteButtonClicked((ObjId)MainPanel.this.objectTable.getValue());
        }
    });
    private final Button upgradeButton = new Button("Upgrade", new Button.ClickListener() {
        @Override
        public void buttonClick(Button.ClickEvent event) {
            MainPanel.this.upgradeButtonClicked((ObjId)MainPanel.this.objectTable.getValue());
        }
    });
    private final Button reloadButton = new Button("Reload", new Button.ClickListener() {
        @Override
        public void buttonClick(Button.ClickEvent event) {
            MainPanel.this.reloadButtonClicked();
        }
    });

    private final HorizontalSplitPanel splitPanel = new HorizontalSplitPanel();
    private final JClassContainer typeContainer = new JClassContainer();
    private final TypeTable typeTable = new TypeTable(this.typeContainer);

    private ObjectTable objectTable = new ObjectTable(Void.class);      // table will be empty
    private JClass<?> jclass;
    private boolean canCreate;

    @Autowired
    @Qualifier("jsimpledbGuiJSimpleDB")
    private JSimpleDB jdb;

    @Autowired
    private ChangePublisher changePublisher;

    public MainPanel() {
        this.setMargin(false);
        this.setSpacing(true);
        this.setHeight("100%");

        // Setup type table
        this.typeTable.setSelectable(true);
        this.typeTable.setImmediate(true);

        // Setup object table, initially showing all objects
        this.objectTable.setSelectable(true);
        this.objectTable.setImmediate(true);

        // Layout top split panel
        this.splitPanel.setWidth("100%");
        this.splitPanel.setHeight(300, Sizeable.Unit.PIXELS);
        this.splitPanel.addComponent(this.typeTable);
        this.splitPanel.addComponent(this.objectTable);
        this.splitPanel.setSplitPosition(20);
        this.addComponent(this.splitPanel);

        // Row with schema version and buttons
        final HorizontalLayout buttonRow = new HorizontalLayout();
        buttonRow.setSpacing(true);
        buttonRow.setWidth("100%");
        buttonRow.addComponent(new SizedLabel("Schema Version " + this.jdb.getLastVersion()));
        final Label spacer1 = new Label();
        buttonRow.addComponent(spacer1);
        buttonRow.setExpandRatio(spacer1, 1.0f);
        buttonRow.addComponent(this.editButton);
        buttonRow.addComponent(this.newButton);
        buttonRow.addComponent(this.deleteButton);
        buttonRow.addComponent(this.upgradeButton);
        buttonRow.addComponent(this.reloadButton);
        this.addComponent(buttonRow);
        this.setComponentAlignment(buttonRow, Alignment.TOP_RIGHT);

        // Add space filler
        final Label spacer2 = new Label();
        this.addComponent(spacer2);
        this.setExpandRatio(spacer2, 1.0f);

        // Listen to type selections
        this.typeTable.addValueChangeListener(new Property.ValueChangeListener() {
            @Override
            public void valueChange(Property.ValueChangeEvent event) {
                final JClassContainer.Node node = MainPanel.this.typeContainer.getJavaObject(
                  (Integer)event.getProperty().getValue());
                if (node != null)
                    MainPanel.this.selectType(node.getJClass());
            }
        });

        // Listen to object selections
        this.objectTable.addValueChangeListener(new Property.ValueChangeListener() {
            @Override
            public void valueChange(Property.ValueChangeEvent event) {
                final JObject jobj = MainPanel.this.objectTable.getContainer().getJavaObject(
                  (ObjId)event.getProperty().getValue());
                MainPanel.this.selectObject(jobj);
            }
        });
    }

    // Invoked when a type is clicked on
    public void selectType(JClass<?> jclass) {
        final ObjectTable newTable = new ObjectTable(jclass);
        this.splitPanel.replaceComponent(this.objectTable, newTable);
        this.objectTable = newTable;
    }

    // Invoked when an object is clicked on
    protected void selectObject(JObject jobj) {

        // Handle de-selection
        if (jobj == null) {
            this.editButton.setEnabled(false);
            this.newButton.setEnabled(false);
            this.deleteButton.setEnabled(false);
            this.upgradeButton.setEnabled(false);
            this.upgradeButton.setEnabled(false);
            return;
        }

        // Update buttons
        this.editButton.setEnabled(true);
        this.newButton.setEnabled(this.canCreate);
        this.deleteButton.setEnabled(true);
        this.upgradeButton.setEnabled(this.canUpgrade(jobj));
    }

// Edit

    private void editButtonClicked(ObjId id) {
        this.log.info("editing object " + id);
        final JObject jobj = this.doCopyForEdit(id);
        if (jobj == null) {
            Notification.show("Object " + id + " no longer exists", null, Notification.Type.WARNING_MESSAGE);
            return;
        }
        new EditWindow(jobj).show();
    }

    @RetryTransaction
    @Transactional("jsimpledbGuiTransactionManager")
    private JObject doCopyForEdit(ObjId id) {
        final JObject jobj = JTransaction.getCurrent().getJObject(id);
        if (!jobj.exists())
            return null;
        return jobj.copyOut();
    }

// New

    private void newButtonClicked() {
        this.log.info("creating new object of type " + this.jclass.getTypeToken());
        this.doCreate();
    }

    @RetryTransaction
    @Transactional("jsimpledbGuiTransactionManager")
    private void doCreate() {
        final TypeToken<?> typeToken = this.jclass.getTypeToken();
        final Object jobj = JTransaction.getCurrent().create(typeToken.getRawType());
        this.changePublisher.publishChangeOnCommit(new ObjectCreate<Object>(jobj));
    }

// Delete

    private void deleteButtonClicked(ObjId id) {
        this.log.info("deleting object " + id);
        if (this.doDelete(id))
            Notification.show("Removed object " + id);
        else
            Notification.show("Object " + id + " no longer exists", null, Notification.Type.WARNING_MESSAGE);
    }

    @RetryTransaction
    @Transactional("jsimpledbGuiTransactionManager")
    private boolean doDelete(ObjId id) {
        final JObject jobj = JTransaction.getCurrent().getJObject(id);
        final boolean deleted = jobj.delete();
        if (deleted)
            this.changePublisher.publishChangeOnCommit(new ObjectDelete<Object>(jobj));
        return deleted;
    }

// Upgrade

    private void upgradeButtonClicked(ObjId id) {
        final int newVersion = this.jdb.getVersion();
        this.log.info("upgrading object " + id + " to schema version " + newVersion);
        final int oldVersion = this.doUpgrade(id);
        switch (oldVersion) {
        case -1:
            Notification.show("Object " + id + " no longer exists", null, Notification.Type.WARNING_MESSAGE);
            break;
        case 0:
            Notification.show("Object " + id + " was already upgraded", null, Notification.Type.WARNING_MESSAGE);
            break;
        default:
            Notification.show("Upgraded object " + id + " version from " + oldVersion + " to " + newVersion);
            break;
        }
    }

    @RetryTransaction
    @Transactional("jsimpledbGuiTransactionManager")
    private int doUpgrade(ObjId id) {
        final JObject jobj = JTransaction.getCurrent().getJObject(id);
        final int oldVersion;
        try {
            oldVersion = jobj.getSchemaVersion();
        } catch (DeletedObjectException e) {
            return -1;
        }
        final boolean upgraded = jobj.upgrade();
        if (upgraded)
            this.changePublisher.publishChangeOnCommit(jobj);
        return upgraded ? oldVersion : 0;
    }

    private boolean canUpgrade(JObject jobj) {
        return jobj != null && jobj.getSchemaVersion() != this.jdb.getLastVersion();
    }

// Reload

    private void reloadButtonClicked() {
        this.objectTable.getContainer().reload();
    }

// EditWindow

    public class EditWindow extends ConfirmWindow {

        private final JObject jobj;
        private final JClass<?> jclass;
        private final LinkedHashMap<String, Component> editorMap = new LinkedHashMap<>();

        EditWindow(JObject jobj) {
            super(MainPanel.this.getUI(), "Edit Object");
            this.setWidth(600, Sizeable.Unit.PIXELS);
            this.setHeight(450, Sizeable.Unit.PIXELS);
            this.jobj = jobj;
            this.jclass = MainPanel.this.jdb.getJClass(this.jobj.getObjId().getStorageId());

            // First introspect for any @FieldBuilder.* annotations
            final Map<String, AbstractField<?>> fieldBuilderFields = new FieldBuilder().buildBeanPropertyFields(this.jobj);
            for (Map.Entry<String, AbstractField<?>> entry : fieldBuilderFields.entrySet()) {
                final String fieldName = entry.getKey();
                final AbstractField<?> field = entry.getValue();
                this.editorMap.put(fieldName, this.buildFieldFieldEditor(fieldName, field));
            }

            // Now build editors for the remaining properties
            for (Map.Entry<String, JField> entry : this.jclass.getJFieldsByName().entrySet()) {
                final String fieldName = entry.getKey();
                if (this.editorMap.containsKey(fieldName))
                    continue;
                final JField jfield = entry.getValue();
                final Component editor;
                if (jfield instanceof JSimpleField)
                    editor = this.buildSimpleFieldEditor((JSimpleField)jfield);
                else
                    continue;       // TODO
                this.editorMap.put(fieldName, editor);
            }
        }

        @Override
        protected void addContent(VerticalLayout layout) {
            layout.addComponent(Component.class.cast(MainPanel.this.objectTable.getContainer().getContainerProperty(
              this.jobj.getObjId(), ObjectContainer.REFERENCE_LABEL_PROPERTY).getValue()));
            final FormLayout formLayout = new FormLayout();
            for (Component component : this.editorMap.values())
                formLayout.addComponent(component);
            layout.addComponent(formLayout);
        }

        @Override
        @RetryTransaction
        @Transactional("jsimpledbGuiTransactionManager")
        protected boolean execute() {
            final JTransaction jtx = JTransaction.getCurrent();

            // Find object
            final ObjId id = this.jobj.getObjId();
            final JObject target = jtx.getJObject(id);
            if (!target.exists()) {
                Notification.show("Object " + id + " no longer exists", null, Notification.Type.WARNING_MESSAGE);
                return true;
            }

            // Copy values
            this.jobj.copyIn();

            // Broadcast update event after successful commit
            MainPanel.this.changePublisher.publishChangeOnCommit(target);

            // Show notification after successful commit
            final VaadinSession session = VaadinUtil.getCurrentSession();
            jtx.getTransaction().addCallback(new Transaction.CallbackAdapter() {
                @Override
                public void afterCommit() {
                    VaadinUtil.invoke(session, new Runnable() {
                        @Override
                        public void run() {
                            Notification.show("Updated object " + id);
                        }
                    });
                }
            });
            return true;
        }

        protected Field<?> buildFieldFieldEditor(String fieldName, AbstractField<?> field) {
            return field;
        }

        protected Field<?> buildSimpleFieldEditor(JSimpleField jfield) {

            // Get field info
            final boolean allowNull = jfield.getGetter().getAnnotation(NotNull.class) == null;

            // Get the property we want to edit
            final Property<?> property = this.buildProperty(jfield);

            // Build editor
            final Field<?> editor = this.buildSimpleFieldEditor(jfield, property, allowNull);
            editor.setCaption(this.buildCaption(jfield.getName()));
            return editor;
        }

        protected Field<?> buildSimpleFieldEditor(JSimpleField jfield, Property<?> property, boolean allowNull) {

            // Get property type
            final Class<?> type = jfield.getGetter().getReturnType();

            // Use ComboBox for references
            if (jfield instanceof JReferenceField) {
                final ReferenceComboBox comboBox = new ReferenceComboBox(type, allowNull);
                comboBox.setPropertyDataSource(property);
                return comboBox;
            }

            // Use ComboBox for Enum's
            if (Enum.class.isAssignableFrom(type)) {
                final EnumComboBox comboBox = this.createEnumComboBox(type.asSubclass(Enum.class), allowNull);
                comboBox.setPropertyDataSource(property);
                if (allowNull)
                    comboBox.setInputPrompt("Null");
                return comboBox;
            }

            // Use text field for all other field types
            final TextField textField = new TextField();
            textField.setWidth("100%");
            if (allowNull)
                textField.setNullRepresentation("");
            textField.setPropertyDataSource(property);
            final FieldType<?> fieldType = this.getFieldType(jfield);
            if (fieldType.getTypeToken().getRawType() != String.class)
                textField.setConverter(this.buildSimpleFieldConverter(fieldType));
            return textField;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private Property<?> buildProperty(JSimpleField jfield) {
            return new MethodProperty(jfield.getGetter().getReturnType(), this.jobj, jfield.getGetter(), jfield.getSetter());
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private <T extends Enum> EnumComboBox createEnumComboBox(Class<T> enumType, boolean allowNull) {
            return new EnumComboBox(enumType, allowNull);
        }

        private <T> SimpleFieldConverter<T> buildSimpleFieldConverter(FieldType<T> fieldType) {
            return new SimpleFieldConverter<T>(fieldType);
        }

        private FieldType<?> getFieldType(JSimpleField jfield) {
            return MainPanel.this.jdb.getDatabase().getFieldTypeRegistry().getFieldType(jfield.getTypeName());
        }

        private String buildCaption(String fieldName) {
            return DefaultFieldFactory.createCaptionByPropertyId(fieldName) + ":";
        }
    }
}

