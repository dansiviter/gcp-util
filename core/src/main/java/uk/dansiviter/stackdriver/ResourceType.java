/*
 * Copyright 2019-2020 Daniel Siviter
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
package uk.dansiviter.stackdriver;

import static java.lang.System.getenv;
import static java.util.Objects.isNull;
import static uk.dansiviter.stackdriver.ResourceType.Label.CLUSTER_NAME;
import static uk.dansiviter.stackdriver.ResourceType.Label.CONTAINER_NAME;
import static uk.dansiviter.stackdriver.ResourceType.Label.INSTANCE_ID;
import static uk.dansiviter.stackdriver.ResourceType.Label.LOCATION;
import static uk.dansiviter.stackdriver.ResourceType.Label.MODULE_ID;
import static uk.dansiviter.stackdriver.ResourceType.Label.NAMESPACE_NAME;
import static uk.dansiviter.stackdriver.ResourceType.Label.POD_NAME;
import static uk.dansiviter.stackdriver.ResourceType.Label.PROJECT_ID;
import static uk.dansiviter.stackdriver.ResourceType.Label.VERSION_ID;
import static uk.dansiviter.stackdriver.ResourceType.Label.ZONE;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import com.google.cloud.MetadataConfig;
import com.google.cloud.MonitoredResource;
import com.google.cloud.MonitoredResource.Builder;
import com.google.cloud.ServiceOptions;

/**
 * Utility to create a {@link MonitoredResource} based on the Stackdriver documentation.
 *
 * @author Daniel Siviter
 * @since v1.0 [6 Dec 2019]
 * @see https://cloud.google.com/monitoring/api/resources
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

	private ResourceType(final String name, final Label... labels) {
		this.name = name;
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
		final Builder builder = MonitoredResource.newBuilder(this.name);
		Arrays.asList(this.labels).forEach(l -> {
			final Optional<String> value = override.apply(l.name);
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
		for (ResourceType type : values()) {
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
		// default Resource type
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


	// --- Inner Classes ---

	/**
	 *
	 */
	public enum Label {
		/**
		 * The identifier of the GCP project associated with this resource, such as "my-project".
		 */
		PROJECT_ID("project_id", ServiceOptions::getDefaultProjectId),
		/**
		 * The numeric VM instance identifier assigned by Compute Engine.
		 */
		INSTANCE_ID("instance_id", MetadataConfig::getInstanceId),
		/**
		 * The Compute Engine zone in which the VM is running.
		 */
		ZONE("zone", MetadataConfig::getZone, () -> getenv("ZONE")),
		/**
		 * The service/module name.
		 */
		MODULE_ID("module_id", () -> getenv("GAE_SERVICE")),
		/**
		 * The version name.
		 */
		VERSION_ID("version_id", () -> getenv("GAE_VERSION")),
		/**
		 * The physical location of the cluster that contains the container.
		 * </p>
		 * This relates to the master node rather than the pod.
		 * https://cloud.google.com/monitoring/kubernetes-engine/migration#resource_type_changes
		 */
		LOCATION("location", MetadataConfig::getZone, () -> getenv("GOOGLE_LOCATION")),
		/**
		 * The name of the cluster that the container is running in.
		 */
		CLUSTER_NAME("cluster_name", MetadataConfig::getClusterName, () -> getenv("CLUSTER_NAME"), () -> getenv("KUBE_CLUSTER")),
		/**
		 * The name of the namespace that the container is running in.
		 */
		NAMESPACE_NAME("namespace_name", MetadataConfig::getNamespaceId, () -> getenv("NAMESPACE_NAME"), () -> getenv("KUBE_NAMESPACE")),
		/**
		 * The name of the pod that the container is running in.
		 */
		POD_NAME("pod_name", () -> getenv("HOSTNAME")),
		/**
		 * The name of the container.
		 */
		CONTAINER_NAME("container_name", MetadataConfig::getContainerName, () -> getenv("CONTAINER_NAME"));


		private final String name;
		private final Supplier<String>[] suppliers;

		@SafeVarargs
		private Label(final String name, final Supplier<String>... suppliers) {
			this.name = name;
			this.suppliers = suppliers;
		}

		/**
		 * @return
		 */
		public Optional<String> get() {
			for (final Supplier<String> supplier : this.suppliers) {
				final String value = supplier.get();
				if (!isNull(value)) {
					return Optional.of(value);
				}
			}
			return Optional.empty();
		}

		public  Optional<String> get(MonitoredResource resource) {
			return ResourceType.get(resource, this);
		}
	}
}
