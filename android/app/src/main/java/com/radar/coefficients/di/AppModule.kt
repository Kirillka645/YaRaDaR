package com.radar.coefficients.di

import android.content.Context
import androidx.room.Room
import com.radar.coefficients.BuildConfig
import com.radar.coefficients.data.local.AppDatabase
import com.radar.coefficients.data.local.dao.CityDao
import com.radar.coefficients.data.local.dao.DemandZoneDao
import com.radar.coefficients.data.local.dao.TariffDao
import com.radar.coefficients.data.remote.api.DemandApi
import com.radar.coefficients.data.repository.CityRepositoryImpl
import com.radar.coefficients.data.repository.DemandRepositoryImpl
import com.radar.coefficients.data.repository.RouteRepositoryImpl
import com.radar.coefficients.data.repository.SettingsRepositoryImpl
import com.radar.coefficients.data.repository.TariffRepositoryImpl
import com.radar.coefficients.data.route.OsrmRouteProvider
import com.radar.coefficients.domain.provider.FareEstimateProvider
import com.radar.coefficients.domain.provider.RouteProvider
import com.radar.coefficients.domain.repository.CityRepository
import com.radar.coefficients.domain.repository.DemandRepository
import com.radar.coefficients.domain.repository.RouteRepository
import com.radar.coefficients.domain.repository.SettingsRepository
import com.radar.coefficients.domain.repository.TariffRepository
import com.radar.coefficients.data.provider.TariffFareEstimateProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val auth = Interceptor { chain ->
            val req = chain.request().newBuilder()
            val token = BuildConfig.DEMAND_API_TOKEN
            if (token.isNotBlank()) {
                req.header("Authorization", "Bearer $token")
            }
            req.header("Accept", "application/json")
            chain.proceed(req.build())
        }
        return OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit {
        val base = BuildConfig.DEMAND_API_BASE_URL.let {
            if (it.endsWith("/")) it else "$it/"
        }
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideDemandApi(retrofit: Retrofit): DemandApi =
        retrofit.create(DemandApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "radar.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun zoneDao(db: AppDatabase): DemandZoneDao = db.demandZoneDao()
    @Provides fun cityDao(db: AppDatabase): CityDao = db.cityDao()
    @Provides fun tariffDao(db: AppDatabase): TariffDao = db.tariffDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun demandRepo(impl: DemandRepositoryImpl): DemandRepository

    @Binds @Singleton
    abstract fun cityRepo(impl: CityRepositoryImpl): CityRepository

    @Binds @Singleton
    abstract fun settingsRepo(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton
    abstract fun routeRepo(impl: RouteRepositoryImpl): RouteRepository

    @Binds @Singleton
    abstract fun tariffRepo(impl: TariffRepositoryImpl): TariffRepository

    @Binds @Singleton
    abstract fun routeProvider(impl: OsrmRouteProvider): RouteProvider

    @Binds @Singleton
    abstract fun fareProvider(impl: TariffFareEstimateProvider): FareEstimateProvider
}
