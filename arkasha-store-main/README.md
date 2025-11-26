# Аркаша: Key-Value база данных  

**Аркаша** — это key-value база данных, построенная по модульному принципу.  
Диаграмма описывает контракты (интерфейсы) и связи между основными компонентами.  

## Основное API  
### `KeyValueStore`  
Минимальный контракт CRUD-операций:  

- `put(key, value)` — сохранить значение;  
- `get(key)` — получить значение по ключу;  
- `delete(key)` — удалить значение;  
- `containsKey(key)` — проверить существование ключа.  

---

## Работа с таблицами (DDL)  
### `Database`  
Интерфейс управления таблицами:  

- `createTable(name, options)` — создать таблицу;  
- `openTable(name)` — открыть таблицу и получить `KeyValueStore`;  
- `dropTable(name)` — удалить таблицу;  
- `listTables()` — получить список таблиц;  
- `close()` — корректно закрыть базу.  

### `TableOptions`  
Задает параметры для таблицы: включение WAL, политика fsync, лимит размера значения.  

### `TableRegistry`  
Позволяет регистрировать сериализаторы для таблиц и открывать их с автоматической обработкой типов.  

### `Table`  
Обёртка над `KeyValueStore`, которая сама сериализует/десериализует значения при `put/get`.  

---

## Персистентность  
### `PersistenceManager`  
Отвечает за хранение данных:  

- `flush()` — сбросить данные на диск;  
- `load()` — загрузить при старте.  

### `WriteAheadLog` (опционально)  
Журнал изменений:  

- `append(record)` — добавить запись;  
- `sync()` — зафиксировать изменения на диске;  
- `replay()` — восстановить состояние после сбоя.  

---

## Конфигурация и метрики  
### `DatabaseConfig`  
Задает основные параметры работы движка (путь к данным, размер кэша, поведение fsync).  

### `Metrics`  
Позволяет наблюдать за системой: размер данных в байтах и количество ключей.  

---

## Движок  
### `StorageEngine`  
Главный интерфейс, объединяющий все подсистемы:  

- реализует `Database`;  
- предоставляет доступ к `PersistenceManager`, `WriteAheadLog`, `DatabaseConfig`, `Metrics`.  

Конкретная реализация (например, **in-memory + flush** или **LSM**) подставляется на уровне движка.  

---

## Расширения
### `Distributed`
Методы для будущих возможностей: репликация и шардинг.

---

## Репликация и шардирование

### Консистентное хеш-кольцо
* Реализация находится в [`engine/ConsistentHashRing.java`](src/main/java/com/example/kvdb/engine/ConsistentHashRing.java).
* Каждая master-slave группа представлена несколькими виртуальными узлами в дереве `SortedMap`, что обеспечивает равномерное распределение ключей и минимальные перемещения при добавлении или удалении шардов.
* Метод [`locate`](src/main/java/com/example/kvdb/engine/ConsistentHashRing.java#L33-L41) вычисляет хеш ключа и возвращает ближайшую группу по часовой стрелке, тем самым распределяя нагрузку по кольцу.

### Master-Slave репликация
* Группа описана в [`engine/MasterSlaveGroup.java`](src/main/java/com/example/kvdb/engine/MasterSlaveGroup.java); мастер и слейвы хранятся в `ClusterNode`, который лениво создаёт `InMemoryKeyValueStore` для каждой таблицы.
* Согласованность обеспечивается методом [`replicateToSlaves`](src/main/java/com/example/kvdb/engine/DistributedTable.java#L198-L211): запись сначала выполняется на мастере, затем значение синхронно распространяется на каждый слейв.
* Удаление данных идёт через [`deleteFromGroup`](src/main/java/com/example/kvdb/engine/DistributedTable.java#L190-L197), который очищает ключи на мастере и всех репликах.

### Обработка запросов таблицы
* Распределённая таблица реализована в [`engine/DistributedTable.java`](src/main/java/com/example/kvdb/engine/DistributedTable.java).
* Для любой операции (`put`, `get`, `delete`, `containsKey`) вычисляется группа через [`locateGroup`](src/main/java/com/example/kvdb/engine/DistributedTable.java#L55-L63), после чего используется мастер или слейвы в зависимости от типа операции.
* При создании новой master-slave группы `ArkashaEngine` регистрирует её с помощью [`registerGroup`](src/main/java/com/example/kvdb/engine/DistributedTable.java#L48-L54), что автоматически подготавливает сторы и подключает шард к таблице.

### Мониторинг шардов и реплик
* DTO для мониторинга описаны в [`api/Distributed.java`](src/main/java/com/example/kvdb/api/Distributed.java) и включают роль узла, список ключей и статистику по шарду.
* `ArkashaEngine` строит отчёт о состоянии через [`describeTableCluster`](src/main/java/com/example/kvdb/engine/ArkashaEngine.java#L222-L227), собирая данные по мастерам и репликам, что позволяет наблюдать за распределением и здоровьем кластера.

---

## UML-диаграмма

![Key-Value DB UML](https://www.plantuml.com/plantuml/png/fLPVR-D447_tfvWt7D4a28cNY3jTsnnIHQjMxHqy81usyIInwknQk-kxC-MaNgNZXKEL44y88SGd22TwX1ujNy7-2dmIPcp7yUPSyK2bHlRCxFn-ysTc_S1OeMqoZpmRsWZXKqoxduaeGHZkWBydBUg96AFfm3_dFy3ZWtqOQZ6nu2Tn82m67Sypfw6CXHPp64V2c6rhTJXABEwDbJm9lqBumFFUWtlq1xjA8v3OQZ6s1lpTuyV3Jg7omXKKKoWOd5YsFlC0teSuiVuffblmX3MTGZR5GjQ1GIajX056Q74b7oaLeP2iAM15A0svtrG_znhm9wX34H5lNM6RbS0lmVbWQl6BBol0jPuo8cejsmcPkiwFK9lGM9HZF11IJ6kGIPIOcL_4eiAW44LA1BN4TVEv3Yrkpr04-sfQUH1nZ3BmDOwL3ceMZaUJoh6BeJ6EH7e7of326Gc3kqfEmXKCntWaRCdrX4ejfsLwnNY6NEv2GKxAi1ZoK7vIHzUQyWCaFsEpoZSvsPCxHDyKHo9LGL3LpAvAf5sBdzUjDHpkGn-acAE9kM71VoH64HgdlPfHLMYAibPO-dosZeaGt4OiWzGeBBQORv4V6offI5Ae3yjN9zc1Ld4Rb5DqwewIU9bWj1-NLlTB0f4ow9Ihc3a7imhigOWUIXO7pY2uDTbGpSMpCc8gGL60gfJhD3zAO-nET4Yj5gNmL7FNQL1FK7F3SThBH8W8I0NKNAYh4ZIO7kEKujHfGRKBmGW6TLrOifuvpHGEH5nbOQindcL6TE4AjK-desP1rYvOMByusURYRnpih2guaToAwanGuDZI7Dsx0DlsLyt1NMD2flzhYLKrUYYdeKJmmtaSuHpf15UaIztFyMtE0mSWfbWTEcjhimgPba0XS_T26Tzwhx59ovuh-wiST3PP3Ns3z-6gS_emIMoY4OnDHkQT0GvDTSC4S5PliN0ix0PbCnFQtQ0Lq356HKhCB8mhzsuMUhslxwyRijuOtUvzj-umQEoFZcFeZkJ0MHsgMWwU2yl7MZ97XnE3fiNMlGROh3c0dPOJRkdeI81yQnXGiiLwtSJ_PLaLaypmMQoOhUSCkWFUedAGwf7kb9QjUUTOQxNhO3s9_SRjx7bIMGGTJcSMrEIT0Sv-ofRP3Vt_bIsoj_ahUhxDhe0_KM3xQ6_0CyvptAU1xPSJsc-CeqV90KQeS4RAMZLde3fiu4lAwOH-VINqruk_NxTKZsdzIdH-ozvco_oRR07trbSSTJWn_9l4osm9-Slyd2nliYiE8h_itKceVJYpd_EBx7LsbL_aB_Y9_N0A-2r_DQW_K4YsWEntWdYHNMzviFfC8IBdJ8CFB_9p-htgmOuOdoPnNwFX9FCd9DaHwJ-oDwJUP1ZXX2lZjdL1hOhobi3hsWo81JrVGf1eCGgZqAP5lduXvWlE5w4i-1YPC6X-IK4jEJmMl8OFkny1cL5ooUobGyRf4wVP6U37WiSrXKl9ocxoRxC_cGevpzwGwXhyK7RdE5SwNNtOkLbZ9qjc2Fid1vqUPDz3_XsvkYL75QChuFSYrEli5lY7a2wun8HyqM3xW9wIUVGl)

## Ориентировочный стек технологий

- **Язык:** Java 21 LTS   
- **Сборка:** предварительно Gradle + Gradle Wrapper  
- **Тесты:** предварительно JUnit 5  
- **Форматирование и линтинг:** предварительно Spotless  
- **Контейнеризация:** Всё окружение планируется запускать через **Docker Compose**  
- **CI/CD:** предварительно Gitlab CI/CD 
- **Документация:** PlantUML для диаграмм, Markdown для описаний, Atlassian Confluence при необходимости