package org.springframework.roo.addon.i18n;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.jvnet.inflector.Noun;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.springframework.roo.addon.i18n.components.I18n;
import org.springframework.roo.addon.i18n.components.I18nSupport;
import org.springframework.roo.addon.web.mvc.controller.addon.responses.ControllerMVCResponseService;
import org.springframework.roo.classpath.ModuleFeatureName;
import org.springframework.roo.classpath.TypeLocationService;
import org.springframework.roo.classpath.details.FieldMetadata;
import org.springframework.roo.classpath.persistence.PersistenceMemberLocator;
import org.springframework.roo.classpath.scanner.MemberDetails;
import org.springframework.roo.model.JavaSymbolName;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.process.manager.FileManager;
import org.springframework.roo.project.FeatureNames;
import org.springframework.roo.project.LogicalPath;
import org.springframework.roo.project.Path;
import org.springframework.roo.project.PathResolver;
import org.springframework.roo.project.ProjectOperations;
import org.springframework.roo.project.maven.Pom;
import org.springframework.roo.propfiles.manager.PropFilesManagerService;
import org.springframework.roo.support.logging.HandlerUtils;
import org.springframework.roo.support.util.XmlUtils;

/**
 * Implementation of {@link I18nOperations}.
 * 
 * @author Sergio Clares
 * @since 2.0
 */
@Component
@Service
public class I18nOperationsImpl implements I18nOperations {

  private static final Logger LOGGER = HandlerUtils.getLogger(I18nOperationsImpl.class);

  // ------------ OSGi component attributes ----------------
  private BundleContext context;

  private TypeLocationService typeLocationService;
  private I18nSupport i18nSupport;
  private PathResolver pathResolver;
  private FileManager fileManager;
  private ProjectOperations projectOperations;
  private PropFilesManagerService propFilesManagerService;
  private PersistenceMemberLocator persistenceMemberLocator;

  protected void activate(final ComponentContext context) {
    this.context = context.getBundleContext();
  }

  @Override
  public boolean isInstallLanguageCommandAvailable() {
    return getProjectOperations().isFeatureInstalled(FeatureNames.MVC);
  }

  public void installI18n(final I18n i18n, final Pom module) {

    // Check if provided module match with application modules features
    Validate.isTrue(getTypeLocationService()
        .hasModuleFeature(module, ModuleFeatureName.APPLICATION),
        "ERROR: Provided module doesn't match with application modules features. "
            + "Execute this operation again and provide a valid application module.");

    Validate.notNull(i18n, "Language choice required");

    if (i18n.getLocale() == null) {
      LOGGER.warning("could not parse language choice");
      return;
    }

    LogicalPath resourcesPath =
        getPathResolver().getPath(module.getModuleName(), Path.SRC_MAIN_RESOURCES);

    final String targetDirectory = getPathResolver().getIdentifier(resourcesPath, "");

    // Install message bundle
    String messageBundle = targetDirectory + "/messages_" + i18n.getLocale().getLanguage() /* + country */
        + ".properties";

    // Special case for english locale (default)
    if (i18n.getLocale().equals(Locale.ENGLISH)) {
      messageBundle = targetDirectory + "/messages.properties";
    }
    if (!getFileManager().exists(messageBundle)) {
      InputStream inputStream = null;
      OutputStream outputStream = null;
      try {
        inputStream = i18n.getMessageBundle();
        outputStream = getFileManager().createFile(messageBundle).getOutputStream();
        IOUtils.copy(inputStream, outputStream);
      } catch (final Exception e) {
        throw new IllegalStateException(
            "Encountered an error during copying of message bundle MVC JSP addon.", e);
      } finally {
        IOUtils.closeQuietly(inputStream);
        IOUtils.closeQuietly(outputStream);
      }
    }

    // Install flag
    final String flagGraphic =
        targetDirectory + "/public/img/" + i18n.getLocale().getLanguage() /* + country */+ ".png";
    if (!getFileManager().exists(flagGraphic)) {
      InputStream inputStream = null;
      OutputStream outputStream = null;
      try {
        inputStream = i18n.getFlagGraphic();
        outputStream = getFileManager().createFile(flagGraphic).getOutputStream();
        IOUtils.copy(inputStream, outputStream);
      } catch (final Exception e) {
        throw new IllegalStateException(
            "Encountered an error during copying of flag graphic for MVC JSP addon.", e);
      } finally {
        IOUtils.closeQuietly(inputStream);
        IOUtils.closeQuietly(outputStream);
      }
    }

    // TODO: get all controllers and update its message bundles

    // Setup language definition in languages.jspx
    //    final String footerFileLocation = targetDirectory + "/WEB-INF/views/footer.jspx";
    //    final Document footer = XmlUtils.readXml(getFileManager().getInputStream(footerFileLocation));
    //
    //    if (XmlUtils.findFirstElement("//span[@id='language']/language[@locale='"
    //        + i18n.getLocale().getLanguage() + "']", footer.getDocumentElement()) == null) {
    //      final Element span =
    //          XmlUtils.findRequiredElement("//span[@id='language']", footer.getDocumentElement());
    //      span.appendChild(new XmlElementBuilder("util:language", footer)
    //          .addAttribute("locale", i18n.getLocale().getLanguage())
    //          .addAttribute("label", i18n.getLanguage()).build());
    //      getFileManager().createOrUpdateTextFileIfRequired(footerFileLocation,
    //          XmlUtils.nodeToString(footer), false);
    //    }
  }

  /**
   * Adds the entity properties as labels into i18n messages file 
   * 
   * @param entityDetails MemberDetails where entity properties are defined
   * @param entity JavaType representing the entity binded to controller
   */
  public void updateI18n(MemberDetails entityDetails, JavaType entity, String moduleName) {

    final Map<String, String> properties = new LinkedHashMap<String, String>();

    final LogicalPath resourcesPath = LogicalPath.getInstance(Path.SRC_MAIN_RESOURCES, moduleName);

    final String entityName = entity.getSimpleTypeName();

    properties.put(buildLabel(entityName, ""), new JavaSymbolName(entity.getSimpleTypeName()
        .toLowerCase()).getReadableSymbolName());

    final String pluralResourceId = buildLabel(entity.getSimpleTypeName(), "plural");
    final String plural = Noun.pluralOf(entity.getSimpleTypeName(), Locale.ENGLISH);
    properties.put(pluralResourceId, new JavaSymbolName(plural).getReadableSymbolName());

    final List<FieldMetadata> javaTypePersistenceMetadataDetails =
        getPersistenceMemberLocator().getIdentifierFields(entity);

    if (!javaTypePersistenceMetadataDetails.isEmpty()) {
      for (final FieldMetadata idField : javaTypePersistenceMetadataDetails) {
        properties.put(buildLabel(entityName, idField.getFieldName().getSymbolName()), idField
            .getFieldName().getReadableSymbolName());
      }
    }

    for (final FieldMetadata field : entityDetails.getFields()) {
      final String fieldResourceId = buildLabel(entityName, field.getFieldName().getSymbolName());

      properties.put(fieldResourceId, field.getFieldName().getReadableSymbolName());
    }

    // Update messages bundles of each installed language
    Set<I18n> supportedLanguages = getI18nSupport().getSupportedLanguages();
    for (I18n i18n : supportedLanguages) {
      String messageBundle =
          String.format("messages_%s.properties", i18n.getLocale().getLanguage());
      if (getFileManager().exists(messageBundle)) {
        getPropFilesManager().addProperties(resourcesPath, messageBundle, properties, true, false);
      }
    }

    // Allways update english message bundles
    getPropFilesManager().addProperties(resourcesPath, "messages.properties", properties, true,
        false);
  }

  /**
   * Builds the label of the specified field and adds it to the entity label
   * 
   * @param entity the entity name
   * @param field the field name
   * @return label
   */
  public static String buildLabel(String entityName, String fieldName) {
    String entityLabel = XmlUtils.convertId("label." + entityName.toLowerCase());

    // If field is blank or null, only entity label will be generated
    if (fieldName != null && StringUtils.isBlank(fieldName)) {
      return entityLabel;
    }

    // Else, is necessary to concat fieldName to generate full field label
    return XmlUtils.convertId(entityLabel.concat(".").concat(fieldName.toLowerCase()));
  }

  /**
   * This method gets all implementations of ControllerMVCResponseService interface to be able
   * to locate all ControllerMVCResponseService. Uses param installed to obtain only the installed
   * or not installed response types.
   * 
   * @param installed indicates if returned responseType should be installed or not.
   * 
   * @return Map with responseTypes identifier and the ControllerMVCResponseService implementation
   */
  public List<ControllerMVCResponseService> getControllerMVCResponseTypes(boolean installed) {
    List<ControllerMVCResponseService> responseTypes =
        new ArrayList<ControllerMVCResponseService>();

    try {
      ServiceReference<?>[] references =
          this.context.getAllServiceReferences(ControllerMVCResponseService.class.getName(), null);

      for (ServiceReference<?> ref : references) {
        ControllerMVCResponseService responseTypeService =
            (ControllerMVCResponseService) this.context.getService(ref);
        boolean isAbleToInstall = false;
        for (Pom module : getProjectOperations().getPoms()) {
          if (responseTypeService.isInstalledInModule(module.getModuleName()) == installed) {
            isAbleToInstall = true;
            break;
          }
        }
        if (isAbleToInstall) {
          responseTypes.add(responseTypeService);
        }
      }
      return responseTypes;

    } catch (InvalidSyntaxException e) {
      LOGGER.warning("Cannot load ControllerMVCResponseService on I18nOperationsImpl.");
      return null;
    }
  }

  // Get OSGi services

  public TypeLocationService getTypeLocationService() {
    if (typeLocationService == null) {
      // Get all Services implement TypeLocationService interface
      try {
        ServiceReference<?>[] references =
            this.context.getAllServiceReferences(TypeLocationService.class.getName(), null);

        for (ServiceReference<?> ref : references) {
          typeLocationService = (TypeLocationService) this.context.getService(ref);
          return typeLocationService;
        }

        return null;

      } catch (InvalidSyntaxException e) {
        LOGGER.warning("Cannot load TypeLocationService on I18nOperationsImpl.");
        return null;
      }
    } else {
      return typeLocationService;
    }
  }

  public I18nSupport getI18nSupport() {
    if (i18nSupport == null) {
      // Get all Services implement I18nSupport interface
      try {
        ServiceReference<?>[] references =
            context.getAllServiceReferences(I18nSupport.class.getName(), null);

        for (ServiceReference<?> ref : references) {
          i18nSupport = (I18nSupport) context.getService(ref);
          return i18nSupport;
        }
        return null;
      } catch (InvalidSyntaxException e) {
        LOGGER.warning("Cannot load I18nSupport on I18nOperationsImpl.");
        return null;
      }
    } else {
      return i18nSupport;
    }
  }

  public PathResolver getPathResolver() {
    if (pathResolver == null) {
      // Get all Services implement PathResolver interface
      try {
        ServiceReference<?>[] references =
            this.context.getAllServiceReferences(PathResolver.class.getName(), null);

        for (ServiceReference<?> ref : references) {
          return (PathResolver) this.context.getService(ref);
        }

        return null;

      } catch (InvalidSyntaxException e) {
        LOGGER.warning("Cannot load PathResolver on I18nOperationsImpl.");
        return null;
      }
    } else {
      return pathResolver;
    }
  }

  public FileManager getFileManager() {
    if (fileManager == null) {
      // Get all Services implement FileManager interface
      try {
        ServiceReference<?>[] references =
            this.context.getAllServiceReferences(FileManager.class.getName(), null);

        for (ServiceReference<?> ref : references) {
          return (FileManager) this.context.getService(ref);
        }

        return null;

      } catch (InvalidSyntaxException e) {
        LOGGER.warning("Cannot load FileManager on I18nOperationsImpl.");
        return null;
      }
    } else {
      return fileManager;
    }
  }

  public ProjectOperations getProjectOperations() {
    if (projectOperations == null) {
      // Get all Services implement ProjectOperations interface
      try {
        ServiceReference<?>[] references =
            this.context.getAllServiceReferences(ProjectOperations.class.getName(), null);

        for (ServiceReference<?> ref : references) {
          projectOperations = (ProjectOperations) this.context.getService(ref);
          return projectOperations;
        }

        return null;

      } catch (InvalidSyntaxException e) {
        LOGGER.warning("Cannot load ProjectOperations on I18nOperationsImpl.");
        return null;
      }
    } else {
      return projectOperations;
    }
  }

  public PropFilesManagerService getPropFilesManager() {
    if (propFilesManagerService == null) {
      // Get all Services implement PropFileOperations interface
      try {
        ServiceReference<?>[] references =
            this.context.getAllServiceReferences(PropFilesManagerService.class.getName(), null);

        for (ServiceReference<?> ref : references) {
          propFilesManagerService = (PropFilesManagerService) this.context.getService(ref);
          return propFilesManagerService;
        }

        return null;

      } catch (InvalidSyntaxException e) {
        LOGGER.warning("Cannot load PropFilesManagerService on I18nOperationsImpl.");
        return null;
      }
    } else {
      return propFilesManagerService;
    }
  }

  public PersistenceMemberLocator getPersistenceMemberLocator() {
    if (persistenceMemberLocator == null) {
      // Get all Services implement TypeLocationService interface
      try {
        ServiceReference<?>[] references =
            context.getAllServiceReferences(PersistenceMemberLocator.class.getName(), null);

        for (ServiceReference<?> ref : references) {
          return (PersistenceMemberLocator) context.getService(ref);
        }

        return null;

      } catch (InvalidSyntaxException e) {
        LOGGER.warning("Cannot load PersistenceMemberLocator on AbstractIdMetadataProvider.");
        return null;
      }
    } else {
      return persistenceMemberLocator;
    }
  }

}