/**
 * Sling Models for BlueShelf components.
 *
 * GOTCHA: bnd only exports packages that carry an OSGi @Version annotation (via package-info.java).
 * HTL's data-sly-use compiles the template into Java and resolves the model class through OSGi,
 * so an un-exported models package yields: "com.blueshelf.core.models.HeroModel cannot be resolved to a type".
 * Bump the version when you change the public API (baseline plugin enforces semantic versioning in AEM archetype).
 */
@org.osgi.annotation.versioning.Version("1.0.0")
package com.blueshelf.core.models;
