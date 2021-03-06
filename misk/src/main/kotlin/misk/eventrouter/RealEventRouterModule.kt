package misk.eventrouter

import com.google.common.util.concurrent.Service
import com.google.inject.Provides
import com.squareup.moshi.Moshi
import misk.healthchecks.HealthCheck
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.inject.to
import misk.moshi.MoshiAdapterModule
import misk.moshi.adapter
import misk.web.WebActionModule
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

class RealEventRouterModule(val environment: Environment) : KAbstractModule() {
  override fun configure() {
    bind<EventRouter>().to<RealEventRouter>().`in`(Singleton::class.java)
    bind<RealEventRouter>().`in`(Singleton::class.java)
    binder().addMultibinderBinding<Service>().to<EventRouterService>()
    binder().addMultibinderBinding<HealthCheck>().to<KubernetesHealthCheck>()
    if (environment == Environment.DEVELOPMENT) {
      bind<ClusterConnector>().to<LocalClusterConnector>()
    } else {
      bind<ClusterConnector>().to<KubernetesClusterConnector>()
    }
    bind<ClusterMapper>().to<ConsistentHashing>()
    install(MoshiAdapterModule(SocketEventJsonAdapter))
    install(WebActionModule.create<EventRouterConnectionAction>())
  }

  @Provides @Singleton @ForEventRouterActions
  fun actionExecutor(): ExecutorService =
      ThreadPoolExecutor(1, 1, 1, TimeUnit.MINUTES, LinkedBlockingQueue())

  // TODO(tso): make this single threaded _per subscriber_ rather than single threaded
  // for everyone.
  @Provides @Singleton @ForEventRouterSubscribers
  fun subscriberExecutor(): ExecutorService =
      ThreadPoolExecutor(1, 1, 1, TimeUnit.MINUTES, LinkedBlockingQueue())

  @Provides @Singleton @ForKubernetesWatching
  fun kubernetesExecutor(): ExecutorService =
      ThreadPoolExecutor(1, 1, 1, TimeUnit.MINUTES, LinkedBlockingQueue())

  @Provides @Singleton
  internal fun provideEventJsonAdapter(moshi: Moshi) = moshi.adapter<SocketEvent>()
}

/**
 * Annotates an executor service that'll be used to process actions. This executor service must
 * not run multiple enqueued tasks concurrently! Instead it should have exactly 1 thread always.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
internal annotation class ForEventRouterActions

/**
 * Annotates an executor service that'll be used to run subscriber notifications (onOpen, onEvent,
 * onClose).
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
internal annotation class ForEventRouterSubscribers

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
internal annotation class ForKubernetesWatching
