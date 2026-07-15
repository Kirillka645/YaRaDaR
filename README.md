# YaRaDaR — Радар коэффициентов

**YaRaDaR** (Yandex Radar Data) — Android-приложение и Official Demand API для водителей: зоны спроса на карте, рейтинг выгоды, тарифы, маршруты.

> **Не является официальным продуктом Яндекса.**  
> Не парсит Яндекс Про, не перехватывает трафик, не требует логин водителя.

## Что внутри

| Компонент | Описание |
|-----------|----------|
| `android/` | Kotlin · Jetpack Compose · Hilt · Room · Clean Architecture |
| `backend/` | **YaRaDaR Official Demand API** (Ktor) |
| Official Engine | Встроенный движок в APK + тот же на сервере |

### Official API

- Источник: **YaRaDaR Official API** (`source_type=OFFICIAL_API`)
- Зоны с коэффициентами, полигонами, сроком актуальности
- Любой город (seed + dynamic по координатам)
- Offline: embedded engine в APK
- Online: `GET /v1/cities/{id}/zones`

```bash
# Backend
cd backend
gradle run
curl http://localhost:8080/health
curl http://localhost:8080/v1/cities/ru-moscow/zones
```

## APK

Собранный debug APK:

```
android/app/build/outputs/apk/debug/app-debug.apk
```

Сборка:

```bash
cd android
./gradlew.bat assembleDebug
# или
./gradlew.bat assembleRelease
```

Установка:

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Опционально в `android/local.properties`:

```properties
MAPS_API_KEY=your_google_maps_key
DEMAND_API_BASE_URL=http://10.0.2.2:8080
```

Без Maps key карта может показывать пустые тайлы; зоны и логика работают.

## Архитектура

```
presentation → domain ← data
                 ↑
     DemandCoefficientProvider
       ├── Official (YaRaDaR)  ← primary
       ├── Licensed
       ├── Community
       └── Demo (optional)
```

## Лицензия данных

Коэффициенты YaRaDaR Official — собственная модель приложения для UI/навигации.  
**Это не коэффициенты Яндекс Про.** Итоговая цена заказа определяется сервисом такси.
