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
import static uk.dansiviter.gcp.AtomicInit.atomic;
import static uk.dansiviter.gcp.ResourceType.Label.CLUSTER_NAME;
import static uk.dansiviter.gcp.ResourceType.Label.CONTAINER_NAME;
import static uk.dansiviter.gcp.ResourceType.Label.INSTANCE_ID;
import static uk.dansiviter.gcp.ResourceType.Label.LOCATION;
import static uk.dansiviter.gcp.ResourceType.Label.MODULE_ID;
import static uk.dansiviter.gcp.ResourceType.Label.NAMESPACE_NAME;
import static uk.dansiviter.gcp.ResourceType.Label.POD_NAME;
import static uk.dansiviter.gcp.ResourceType.Label.PROJECT_ID;
import static uk.dansiviter.gcp.ResourceType.Label.REVISION_NAME;
import static uk.dansiviter.gcp.ResourceType.Label.SERVICE_NAME;
import static uk.dansiviter.gcp.ResourceType.Label.VERSION_ID;
import static uk.dansiviter.gcp.ResourceType.Label.ZONE;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.cloud.MetadataConfig;
import com.google.cloud.MonitoredResource;
import com.google.cloud.ServiceOptions;

/**
 * Utility to create a {@link MonitoredResource} based on the Cloud Operations Suite documentation. It will attempt to
 * load the data from the environment but all values can be overridden via System properties (prefix
 * {@code gcp.cloud.resource.}) or environment parameter (in uppercase). This is inspired by
 * {@code com.google.cloud.logging.MonitoredResourceUtil} but more flexible.
 *
 * @author Daniel Siviter
 * @since v1.0 [6 Dec 2019]
 * @see <a href="https://cloud.google.com/monitoring">Cloud Monitoring</a>
 * @see <a href="https://cloud.google.com/logging">Cloud Logging</a>
 */
public enum ResourceType {
	/**
	 * A virtual machine instance hosted in Google Compute Engine (GCE).
	 */
	GCE_INSTANCE("gce_instance", PROJECT_ID, INSTANCE_ID, ZONE),
	/**
	 * An application running in Google App Engine (GAE).
	 */
	GAE_APP("gae_app", PROJECT_ID, MODULE_ID, VERSION_ID),
	/**
	 * An application running in Google App Engine (GAE) Flex.
	 */
	GAE_APP_FLEX("gae_app_flex", PROJECT_ID, MODULE_ID, VERSION_ID, ZONE),
	/**
	 * A Kubernetes container instance.
	 * <p>
	 * This has replaced 'container' for logs and 'gke_container' for metrics:
	 * https://cloud.google.com/monitoring/kubernetes-engine/migration#what-is-changing
	 */
	K8S_CONTAINER("k8s_container", PROJECT_ID, LOCATION, CLUSTER_NAME, NAMESPACE_NAME, POD_NAME, CONTAINER_NAME),
	/**
	 * A revision in Cloud Run (fully managed).
	 */
	CLOUD_RUN("cloud_run_revision",  REVISION_NAME, SERVICE_NAME, LOCATION),
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
	 * @return the monitored resource instance for this resource type.
	 */
	public MonitoredResource toMonitoredResource() {
		return monitoredResource(this);
	}

	/**
	 * @param override ability to override the default values.
	 * @return the monitored resource instance for this resource type.
	 */
	public MonitoredResource toMonitoredResource(Function<String, Optional<String>> override) {
		return monitoredResource(this, override);
	}


	// --- Static Methods ---

	/**
	 * @param name name of resource type.
	 * @return the found resource.
	 * @throws IllegalArgumentException if resource not found.
	 */
	public static ResourceType fromString(String name) {
		for (var type : values()) {
			if (type.name.equalsIgnoreCase(name)) {
				return type;
			}
		}
		return valueOf(name);
	}

	/**
	 * Attempts to auto-detect resource type.
	 *
	 * @return the resource type.
	 */
	public static Optional<ResourceType> autoDetect() {
		if (getenv("K_SERVICE") != null
				&& getenv("K_REVISION") != null
				&& getenv("K_CONFIGURATION") != null
				&& getenv("KUBERNETES_SERVICE_HOST") == null)
		{
			return Optional.of(CLOUD_RUN);
		}
		if (System.getenv("GAE_INSTANCE") != null) {
			return Optional.of(GAE_APP_FLEX);
		}
		if (System.getenv("KUBERNETES_SERVICE_HOST") != null) {
			return Optional.of(K8S_CONTAINER);
		}
		if (ServiceOptions.getAppEngineAppId() != null) {
			return Optional.of(GAE_APP);
		}
		if (MetadataConfig.getInstanceId() != null) {
			return Optional.of(GCE_INSTANCE);
		}
		return Optional.empty();
	}

	/**
	 * @return the created monitored instance.
	 */
	public static MonitoredResource monitoredResource() {
		return monitoredResource(n -> Optional.empty());
	}

	/**
	 * @param override ability to override the default values.
	 * @return the created monitored instance.
	 */
	public static MonitoredResource monitoredResource(Function<String, Optional<String>> override) {
		return monitoredResource(autoDetect().orElse(GLOBAL), override);
	}

	/**
	 * @param type the resource type;
	 * @return the created monitored instance.
	 */
	public static MonitoredResource monitoredResource(ResourceType type) {
		return monitoredResource(type, n -> Optional.empty());
	}

	/**
	 * @param type the resource type;
	 * @param override ability to override the default values.
	 * @return the created monitored instance.
	 */
	public static MonitoredResource monitoredResource(ResourceType type, Function<String, Optional<String>> override) {
		var builder = MonitoredResource.newBuilder(type.name);
		Arrays.asList(type.labels).forEach(l -> {
			var value = override.apply(l.name);
			value.ifPresentOrElse(
				v -> builder.addLabel(l.name, v),
				() -> l.get().ifPresent(v -> builder.addLabel(l.name, v)));
		});
		return builder.build();
	}

	/**
	 * Extracts the value for the monitored resource label.
	 *
	 * @param resource the resource to use.
	 * @param key the key of the label.
	 * @return the value.
	 */
	public static Optional<String> label(MonitoredResource resource, Label key) {
		return Optional.ofNullable(resource.getLabels().get(key.name));
	}

	/**
	 * Extracts the {@code project_id}.
	 *
	 * @param resource the resource to use.
	 * @return the value.
	 */
	public static Optional<String> projectId(MonitoredResource resource) {
		return Label.PROJECT_ID.get(resource);
	}


	// --- Inner Classes ---

	/**
	 * @see <a href="https://cloud.google.com/kubernetes-engine/docs/how-to/workload-identity#gke_mds">GKE MDS</a>
	 */
	public enum Label {
		/**
		 * The identifier of the GCP project associated with this resource, such as
		 * "my-project".
		 */
		PROJECT_ID("project_id", true, ServiceOptions::getDefaultProjectId),
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
		MODULE_ID("module_id", () -> getenv("GAE_SERVICE")),
		/**
		 * The version name.
		 */
		VERSION_ID("version_id", () -> getenv("GAE_VERSION")),
		/**
		 * The physical location of the cluster that contains the container.
		 * <p>
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
		POD_NAME("pod_name", () -> getenv("HOSTNAME")),
		/**
		 * The name of the container.
		 */
		CONTAINER_NAME("container_name", MetadataConfig::getContainerName),
		/**
		 * Cloud Run/Knative revision.
		 */
		REVISION_NAME("revision_name", () -> getenv("K_REVISION")),
		/**
		 * K8s/Cloud Run/Knative service name.
		 */
		SERVICE_NAME("service_name", () -> getenv("K_SERVICE"));

		private final String name;
		private final Supplier<Optional<String>> supplier;

		private Label(String name, Supplier<String> supplier) {
			this(name, false, supplier);
		}

		private Label(String name, boolean cache, Supplier<String> supplier) {
			this.name = requireNonNull(name);
			Supplier<Optional<String>> delegate = () -> get(name, supplier);
			this.supplier = cache ? atomic(delegate) : delegate;
		}

		public Optional<String> get() {
			return this.supplier.get();
		}

		/**
		 * @return the value of the label.
		 */
		public static Optional<String> get(String name, Supplier<String> supplier) {
			var value = Optional.ofNullable(getenv(name.toUpperCase()));
			if (value.isPresent()) {
				return value;
			}
			value = Optional.ofNullable(System.getProperty("gcp.cloud.resource.".concat(name)));
			if (value.isPresent()) {
				return value;
			}
			var strValue = supplier.get();
			if (!isNull(strValue) && !strValue.isEmpty()) {
				return Optional.of(strValue);
			}
			return Optional.empty();
		}

		/**
		 * @param resource the resource to extract from.
		 * @return the value.
		 */
		public Optional<String> get(MonitoredResource resource) {
			return ResourceType.label(resource, this);
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
