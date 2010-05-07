package org.springframework.roo.addon.solr;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.springframework.roo.addon.web.mvc.controller.WebScaffoldMetadata;
import org.springframework.roo.addon.web.mvc.controller.WebScaffoldMetadataProvider;
import org.springframework.roo.classpath.PhysicalTypeIdentifier;
import org.springframework.roo.classpath.PhysicalTypeMetadata;
import org.springframework.roo.classpath.itd.AbstractItdMetadataProvider;
import org.springframework.roo.classpath.itd.ItdTypeDetailsProvidingMetadataItem;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.project.Path;

/**
 * Provides {@link SolrWebSearchMetadata}.
 * 
 * @author Stefan Schmidt
 * @since 1.1
 *
 */
@Component(immediate=true)
@Service
public final class SolrWebSearchMetadataProvider extends AbstractItdMetadataProvider {
	
	@Reference private WebScaffoldMetadataProvider webScaffoldMetadataProvider;

	protected void activate(ComponentContext context) {
		metadataDependencyRegistry.registerDependency(PhysicalTypeIdentifier.getMetadataIdentiferType(), getProvidesType());
		webScaffoldMetadataProvider.addMetadataTrigger(new JavaType(RooSolrWebSearchable.class.getName()));
		addMetadataTrigger(new JavaType(RooSolrWebSearchable.class.getName()));	
	}
	
	protected ItdTypeDetailsProvidingMetadataItem getMetadata(String metadataIdentificationString, JavaType aspectName, PhysicalTypeMetadata governorPhysicalTypeMetadata, String itdFilename) {
		// Acquire bean info (we need getters details, specifically)
		JavaType javaType = SolrWebSearchMetadata.getJavaType(metadataIdentificationString);
		Path path = SolrWebSearchMetadata.getPath(metadataIdentificationString);
		String webScaffoldMetadataKey = WebScaffoldMetadata.createIdentifier(javaType, path);
		
		// We need to parse the annotation, which we expect to be present
		SolrWebSearchAnnotationValues annotationValues = new SolrWebSearchAnnotationValues(governorPhysicalTypeMetadata);
		if (!annotationValues.isAnnotationFound() || annotationValues.searchMethod == null) {
			return null;
		}
		
		// We want to be notified if the getter info changes in any way 
		metadataDependencyRegistry.registerDependency(webScaffoldMetadataKey, metadataIdentificationString);
		WebScaffoldMetadata webScaffoldMetadata = (WebScaffoldMetadata) metadataService.get(webScaffoldMetadataKey);
		
		// Abort if we don't have getter information available
		if (webScaffoldMetadata == null) {
			return null;
		}

		// Otherwise go off and create the to String metadata
		return new SolrWebSearchMetadata(metadataIdentificationString, aspectName, governorPhysicalTypeMetadata, metadataService, annotationValues, webScaffoldMetadata);
	}
	
	public String getItdUniquenessFilenameSuffix() {
		return "SolrWebSearch";
	}

	protected String getGovernorPhysicalTypeIdentifier(String metadataIdentificationString) {
		JavaType javaType = SolrWebSearchMetadata.getJavaType(metadataIdentificationString);
		Path path = SolrWebSearchMetadata.getPath(metadataIdentificationString);
		String physicalTypeIdentifier = PhysicalTypeIdentifier.createIdentifier(javaType, path);
		return physicalTypeIdentifier;
	}
	
	protected String createLocalIdentifier(JavaType javaType, Path path) {
		return SolrWebSearchMetadata.createIdentifier(javaType, path);
	}

	public String getProvidesType() {
		return SolrWebSearchMetadata.getMetadataIdentiferType();
	}
}