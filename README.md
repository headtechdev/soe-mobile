# SOE Campo — App Operacional

Aplicativo Android de execução em campo do **SOE — Sistema Operacional de Eventos**.
Trabalha offline por padrão: tudo o que o operador registra vai primeiro para o
SQLite local e sobe para o Web SaaS quando houver rede.

- **Stack:** Kotlin + Jetpack Compose (Material 3), Room (SQLite), Retrofit +
  kotlinx.serialization, WorkManager, DataStore.
- **minSdk 26 · targetSdk 35 · Java 17**
- **Pacote:** `br.com.soe.campo`

## Telas

| Tela | Arquivo | O que faz |
| --- | --- | --- |
| Login simplificado | `ui/screens/LoginScreen.kt` | Login persistente (token no DataStore) e configuração do servidor do evento |
| Tela inicial | `ui/screens/HomeScreen.kt` | Estado do sincronismo + as três ações de campo |
| Reportar incidente | `ui/screens/ReportIncidentScreen.kt` | Tipo, severidade, área, local, GPS, fotos — grava offline |
| Ocorrências | `ui/screens/IncidentsScreen.kt` | Lista o que foi registrado, marcando o que ainda está na fila |
| Checklists | `ui/screens/ChecklistsScreen.kt` | Modelos liberados e aplicações recentes |
| Execução de checklist | `ui/screens/ChecklistRunScreen.kt` | Conforme / Não conforme / N-A, observação e evidência fotográfica |
| Alertas | `ui/screens/AlertsScreen.kt` | Geral, por área e crítico, com confirmação de ciência |
| Perfil | `ui/screens/ProfileScreen.kt` | Nome, função, área, turno, check-in/out e estado da fila |

## Regras que o app aplica em campo

As mesmas travas valem no Web SaaS — uma regra que só existe de um lado não é
uma regra:

- **Checklist não conclui** com item obrigatório em branco ou sem a evidência
  fotográfica quando o modelo exige. "Não aplicável" conta como resposta.
- **Rascunho nunca se perde:** o bloqueio acontece depois de gravar o que já foi
  preenchido.
- **Alerta com repasse:** quando o supervisor recebe um alerta em nome de gente
  sem app, a tela diz quantas pessoas dependem dele e o botão vira "Confirmar
  ciência minha e da equipe" (`relayCount`, vindo do servidor).

## Como o sincronismo funciona

O ciclo é sempre **push → pull** (`data/SyncRepository.kt`):

1. **Push** sobe o que foi produzido offline. Cada registro criado em campo
   recebe um **UUID gerado no aparelho**, que vira a chave primária no servidor.
   Reenviar o mesmo lote após uma falha de rede não duplica nada — o servidor
   responde com os ids aceitos, e só esses saem da fila local.
2. **Pull** baixa o que mudou desde o último `serverTime`, incluindo registros
   apagados (tombstones), para o banco local espelhar as exclusões.

O `serverTime` só avança quando o pull chega inteiro: se a conexão cair no meio,
a próxima tentativa refaz o mesmo intervalo sem perder registro.

Registros com fila local pendente **não são sobrescritos** pelo pull — o que o
operador digitou tem precedência até ser confirmado pelo servidor.

O `SyncWorker` (WorkManager) roda a cada 15 minutos exigindo rede, e também sob
demanda depois de cada gravação em campo.

## Rodando

Este projeto **ainda não foi compilado** — a máquina onde foi gerado não tem
Gradle instalado. Para a primeira build:

1. Abra a pasta `E:\SOE MOBILE` no Android Studio.
2. O Studio provisiona o Gradle 8.11.1 indicado em
   `gradle/wrapper/gradle-wrapper.properties` e sincroniza o projeto.
3. Para gerar o `gradlew`/`gradlew.bat` e poder compilar pela linha de comando,
   rode uma vez, no terminal do Studio: `gradle wrapper`.

### Apontando para o servidor

O padrão assado no APK é o servidor de produção:

```
https://soe-web-production-0f27.up.railway.app/
```

Ele fica em `app/build.gradle.kts` (`DEFAULT_API_URL`) e pode ser trocado em
tempo de execução na tela de login ("Configurar servidor"), sem recompilar —
é assim que se aponta o app para um servidor local durante o evento
(`http://<ip-da-maquina>:3000/`) ou para o emulador (`http://10.0.2.2:3000/`).

O `usesCleartextTraffic="true"` do manifesto existe para permitir HTTP em rede
local de evento. **Em produção, use HTTPS e remova essa flag.**

### Credenciais de teste

Vindas do seed do Web SaaS (senha `soe123456`):

- `campo@soe.local` — agente de segurança, Portão A
- `campo2@soe.local` — socorrista, Posto Médico Central

## Estrutura

```
app/src/main/java/br/com/soe/campo/
├── SoeApplication.kt          container de dependências (DI manual)
├── data/
│   ├── SessionStore.kt        token persistente + contexto do evento (DataStore)
│   ├── FieldRepository.kt     fonte de verdade das telas; grava sempre local primeiro
│   ├── SyncRepository.kt      motor push/pull
│   ├── Time.kt                ISO-8601 UTC ↔ epoch local
│   ├── local/                 Room: entidades, DAOs, database
│   └── remote/                Retrofit: contrato e modelos da API
├── sync/SyncWorker.kt         sincronismo periódico e sob demanda
└── ui/                        Compose: tema, navegação, telas, ViewModel
```
