/*
 * Copyright 2019-2021 Daniel Siviter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.dansiviter.gcp;

import static java.lang.System.getenv;
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;
import static uk.dansiviter.gcp.ResourceType.Label.CLUSTER_NAME;
import static uk.dansiviter.gcp.ResourceType.Label.CONTAINER_NAME;
import static uk.dansiviter.gcp.ResourceType.Label.INSTANCE_ID;
import static uk.dansiviter.gcp.ResourceType.Label.LOCATION;
import static uk.dansiviter.gcp.ResourceType.Label.MODULE_ID;
import static uk.dansiviter.gcp.ResourceType.Label.NAMESPACE_NAME;
import static uk.dansiviter.gcp.ResourceType.Label.POD_NAME;
import static uk.dansiviter.gcp.ResourceType.Label.PROJECT_ID;
import static uk.dansiviter.gcp.ResourceType.Label.VERSION_ID;
import static uk.dansiviter.gcp.ResourceType.Label.ZONE;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import com.google.cloud.MetadataConfig;
import com.google.cloud.MonitoredResource;
import com.google.cloud.ServiceOptions;

import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Utility to create a {@link MonitoredResource} based on the Cloud Operations
 * Suite documentation. It will attempt to load the data from the environment
 * but all values can be overriden via Microprofile Config.
 *
 * @author Daniel Siviter
 * @since v1.0 [6 Dec 2019]
 * @see https://cloud.google.com/monitoring
 * @see https://cloud.google.com/logging
 */
public enum ResourceType {
	/**
	 * A virtual machine instance hosted in Google Compute Engine (GCE).
	 */
	GCE_INSTANCE("gce_instance", PROJECT_ID, INSTANCE_ID, ZONE),
	/**
	 * An application running in Google App Engine (GAE).
	 */
	GAE_APP("gae_app", PROJECT_ID, MODULE_ID, VERSION_ID, ZONE),
	/**
	 * A Kubernetes container instance.
	 * </p>
	 * This has replaced 'container' for logs and 'gke_container' for metrics:
	 * https://cloud.google.com/monitoring/kubernetes-engine/migration#what-is-changing
	 */
	K8S_CONTAINER("k8s_container", PROJECT_ID, LOCATION, CLUSTER_NAME, NAMESPACE_NAME, POD_NAME, CONTAINER_NAME),
	/**
	 * A resource type that is not associated with any specific resource.
	 */
	GLOBAL("global", PROJECT_ID);

	private final String name;
	private final Label[] labels;

	private ResourceType(String name, Label... labels) {
		this.name = requireNonNull(name);
		this.labels = labels;
	}

	/**
	 * @return the created monitored instance.
	 */
	public MonitoredResource monitoredResource() {
		return monitoredResource(n -> Optional.empty());
	}

	/**
	 * @param override ability to override the default values.
	 * @return the created monitored instance.
	 */
	public MonitoredResource monitoredResource(@Nonnull Function<String, Optional<String>> override) {
		var builder = MonitoredResource.newBuilder(this.name);
		Arrays.asList(this.labels).forEach(l -> {
			var value = override.apply(l.name);
			value.ifPresentOrElse(v -> builder.addLabel(l.name, v), () -> {
				l.get().ifPresent(v -> builder.addLabel(l.name, v));
			});
		});
		return builder.build();
	}


	// --- Static Methods ---

	/**
	 *
	 * @param name
	 * @return
	 */
	public static ResourceType fromString(@Nonnull String name) {
		for (var type : values()) {
			if (name.equalsIgnoreCase(type.name)) {
				return type;
			}
		}
		return valueOf(name);
	}

	/**
	 *
	 * @return
	 */
	public static ResourceType autoDetect() {
		if (!isNull(getenv("KUBERNETES_SERVICE_HOST"))) {
			return K8S_CONTAINER;
		}
		if (!isNull(getenv("GAE_INSTANCE"))) {
			return GAE_APP;
		}
		if (MetadataConfig.getInstanceId() != null) {
			return GCE_INSTANCE;
		}
		return GLOBAL;
	}

	/**
	 *
	 * @param resource
	 * @param key
	 * @return
	 */
	public static Optional<String> get(MonitoredResource resource, Label key) {
		return Optional.of(resource.getLabels().get(key.name));
	}

	private static Optional<String> config(String name) {
		return ConfigProvider.getConfig().getOptionalValue(name, String.class);
	}

	private static Supplier<String> env(String name) {
		return () -> getenv(name);
	}

	// --- Inner Classes ---

	/**
	 * @see https://cloud.google.com/kubernetes-engine/docs/how-to/workload-identity#gke_mds
	 */
	public enum Label {
		/**
		 * The identifier of the GCP project associated with this resource, such as
		 * "my-project".
		 */
		PROJECT_ID("project_id", ServiceOptions::getDefaultProjectId),
		/**
		 * The numeric VM instance identifier assigned by Compute Engine.
		 */
		INSTANCE_ID("instance_id", MetadataConfig::getInstanceId),
		/**
		 * The Compute Engine zone in which the VM is running.
		 */
		ZONE("zone", MetadataConfig::getZone),
		/**
		 * The service/module name.
		 */
		MODULE_ID("module_id", env("GAE_SERVICE")),
		/**
		 * The version name.
		 */
		VERSION_ID("version_id", env("GAE_VERSION")),
		/**
		 * The physical location of the cluster that contains the container.
		 * </p>
		 * This relates to the master node rather than the pod.
		 * https://cloud.google.com/monitoring/kubernetes-engine/migration#resource_type_changes
		 */
		LOCATION("location", Label::getLocation),
		/**
		 * The name of the cluster that the container is running in.
		 */
		CLUSTER_NAME("cluster_name", MetadataConfig::getClusterName),
		/**
		 * The name of the namespace that the container is running in.
		 */
		NAMESPACE_NAME("namespace_name", MetadataConfig::getNamespaceId),
		/**
		 * The name of the pod that the container is running in.
		 */
		POD_NAME("pod_name", env("HOSTNAME")),
		/**
		 * The name of the container.
		 */
		CONTAINER_NAME("container_name", MetadataConfig::getContainerName);

		private final String name;
		private final Supplier<String>[] suppliers;

		@SafeVarargs
		private Label(String name, Supplier<String>... suppliers) {
			this.name = requireNonNull(name);
			this.suppliers = suppliers;
		}

		/**
		 * @return
		 */
		public Optional<String> get() {
			var value = config(this.name.toUpperCase());
			if (value.isPresent()) {
				return value;
			}
			value = config("gcp.cloud.resource.".concat(this.name));
			if (value.isPresent()) {
				return value;
			}
			for (final Supplier<String> supplier : this.suppliers) {
				var strValue = supplier.get();
				if (!isNull(strValue) && !strValue.isEmpty()) {
					return Optional.of(strValue);
				}
			}
			return Optional.empty();
		}

		public  Optional<String> get(MonitoredResource resource) {
			return ResourceType.get(resource, this);
		}

		private static String getLocation() {
			var zone = MetadataConfig.getZone();
			if (zone != null && zone.endsWith("-1")) {
				return zone.substring(0, zone.length() - 2);
			}
			return zone;
		}
	}
}
