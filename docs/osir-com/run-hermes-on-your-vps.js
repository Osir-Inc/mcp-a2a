/*
 * osir.com help article: "Run Hermes Agent on your own VPS"
 *
 * Paste-ready for the osir.com site repo (nameal), src/data/helpArticles.js:
 *   1. EN: add HELP_ARTICLE_EN to helpArticles.en.vps (the VPS category).
 *   2. DE: add the HELP_ARTICLE_DE entry to deArticleTranslations (keyed by slug) - the site keeps
 *      EN/DE parity, and translatedHelpSlugs picks it up from that map.
 *   3. public/llms.txt: add LLMS_TXT_LINE under the help articles.
 *   4. Run `npm run seo:coverage` (sitemap, feed and llms.txt must agree).
 *
 * PUBLISH ONLY AFTER Hermes is released for good on the deploy backend (pinned digest in its
 * application.properties, not the temporary test env line) and v2.5.1+ is live on the MCP server.
 * Before that, an install may answer "not available yet".
 *
 * Facts in this article come from the shipped tools (v2.5.0). If any of these change, update it:
 * 4 GB minimum RAM; providers deepseek/openai/openrouter/anthropic; password shown once; delete keeps
 * the data folder and does not cancel the VPS; logs not available yet.
 */

export const HELP_ARTICLE_EN = {
  id: 'run-hermes-agent-on-your-vps',
  slug: 'run-hermes-agent-on-your-vps',
  title: 'Run Hermes Agent on your own VPS',
  keywords: ['Hermes', 'Hermes Agent', 'Nous Research', 'AI agent', 'self-hosted', 'personal agent', 'VPS', 'install', 'Claude', 'MCP', 'DeepSeek'],
  content: `
## Run Hermes Agent on your own VPS

You can install Hermes Agent on your own OSIR VPS by asking Claude: "Install Hermes on my VPS". OSIR sets it up for you, behind HTTPS with a password-protected dashboard, in a few minutes. You do not need SSH, Docker or a config file.

**Hermes Agent** is an open-source, self-hosted AI agent by Nous Research. It runs on your own server and you use it through a web dashboard. Because it runs on your VPS, your conversations and memories stay on a machine you control.

### What do you need?

| You need | Details |
|:---|:---|
| OSIR in Claude | Add OSIR as a custom connector: see [Connect OSIR to Claude](/en/help/connect-claude-connector/) |
| An OSIR account | You log in inside the chat when Claude asks |
| A VPS with at least 4 GB RAM | One you already have at OSIR, or Claude can order one for you |
| An API key from a model provider | DeepSeek, OpenAI, OpenRouter or Anthropic. Hermes uses it to think |

### How do you install Hermes?

1. In Claude, say **"Install Hermes on my VPS"**.
2. Claude shows your OSIR servers. Pick one with at least 4 GB RAM. If you have none, Claude offers a VPS package with its price and orders it only after you confirm.
3. Optionally give a domain, for example \`agent.example.com\`. If its DNS is hosted at OSIR, it is connected for you. If not, Claude tells you which A record to add at your DNS provider.
4. Claude shows the **dashboard address, the username and the password**.

> **Save the password now.** It is shown only once and OSIR cannot show it again. If you lose it, ask Claude to reset it.

The first install takes several minutes, because the VPS downloads Hermes. Ask Claude **"Is my Hermes ready?"** until it says installed.

### How do you connect Hermes to an AI model?

Hermes needs a model provider before it can answer. Say for example **"Connect my Hermes to DeepSeek"** and give Claude your API key.

- Supported providers: **DeepSeek, OpenAI, OpenRouter and Anthropic**.
- Your key is stored encrypted and is never shown again. It is not written into any file you can see.
- Hermes restarts with the new provider. This takes a minute or two.

Then open the dashboard address, log in with your username and password, and start chatting.

### Is it secure?

- **Password-protected:** the dashboard asks for a login. Claude can check this for you: ask "Is my Hermes dashboard protected?"
- **HTTPS:** the dashboard is served over an encrypted connection.
- **Your server only:** Hermes runs on your VPS, not on shared OSIR servers.
- **One-time password:** OSIR shows the password once and keeps no readable copy; your API keys are stored encrypted and never shown again. Both do appear in your Claude conversation, so if other people can see that chat, reset the password and replace the key afterwards.

### Common tasks

| You want to | Say to Claude |
|:---|:---|
| Check that it works | "Is my Hermes ready?" |
| Change the AI model provider | "Connect my Hermes to OpenAI" |
| Get a new password | "Reset my Hermes password" |
| See your agents | "Which AI agents do I have?" |
| Remove Hermes | "Remove my Hermes" |

Settings you change inside the Hermes dashboard are replaced the next time you change a setting through Claude. Your conversations, skills and memories are kept.

### What happens when you remove Hermes?

Claude asks you to confirm first. Then Hermes is stopped and removed from your VPS. Its data folder, with your conversations and memories, stays on the VPS. Your VPS is **not** cancelled; cancel it separately if you no longer need it.

### What does it cost?

You pay for your VPS as usual. The AI model provider bills its usage directly to the account that owns the API key you connected.

### Troubleshooting

| Claude says | What to do |
|:---|:---|
| The VPS is too small | Use a VPS with at least 4 GB RAM |
| The VPS refused the OSIR deploy key | Add the key Claude shows to \`/root/.ssh/authorized_keys\` on the VPS, then ask Claude to try again |
| Another program holds ports 80/443 | Stop the other web server on the VPS (for example nginx), then ask Claude to try again |
| Hermes is not available yet | Hermes is not released on OSIR yet. Nothing was installed |
| Logs are not available yet | Ask "Is my Hermes ready?" instead; logs from your VPS are not available through OSIR yet |
| You lost the password | Say "Reset my Hermes password" |

Trying the install again is always safe: it continues where it stopped, never creates a second password and never orders a second VPS.
`
}

export const HELP_ARTICLE_DE = {
  'run-hermes-agent-on-your-vps': {
    title: 'Hermes Agent auf dem eigenen VPS betreiben',
    content: `
## Hermes Agent auf dem eigenen VPS betreiben

Sie können Hermes Agent auf Ihrem eigenen OSIR-VPS installieren, indem Sie Claude bitten: „Installiere Hermes auf meinem VPS". OSIR richtet ihn in wenigen Minuten für Sie ein, hinter HTTPS und mit einem passwortgeschützten Dashboard. Sie brauchen weder SSH noch Docker noch eine Konfigurationsdatei.

**Hermes Agent** ist ein quelloffener, selbst gehosteter KI-Agent von Nous Research. Er läuft auf Ihrem eigenen Server, und Sie nutzen ihn über ein Web-Dashboard. Weil er auf Ihrem VPS läuft, bleiben Ihre Unterhaltungen und Erinnerungen auf einem Rechner, den Sie kontrollieren.

### Was brauchen Sie?

| Sie brauchen | Details |
|:---|:---|
| OSIR in Claude | Fügen Sie OSIR als benutzerdefinierten Connector hinzu: siehe [OSIR mit Claude verbinden](/de/help/connect-claude-connector/) |
| Ein OSIR-Konto | Sie melden sich im Chat an, wenn Claude danach fragt |
| Einen VPS mit mindestens 4 GB RAM | Einen, den Sie bei OSIR schon haben, oder Claude bestellt einen für Sie |
| Einen API-Schlüssel eines Modellanbieters | DeepSeek, OpenAI, OpenRouter oder Anthropic. Hermes nutzt ihn zum Denken |

### Wie installieren Sie Hermes?

1. Sagen Sie in Claude **„Installiere Hermes auf meinem VPS"**.
2. Claude zeigt Ihre OSIR-Server. Wählen Sie einen mit mindestens 4 GB RAM. Haben Sie keinen, schlägt Claude ein VPS-Paket mit Preis vor und bestellt es erst nach Ihrer Bestätigung.
3. Optional geben Sie eine Domain an, zum Beispiel \`agent.example.com\`. Wird ihr DNS bei OSIR gehostet, wird sie automatisch verbunden. Sonst nennt Ihnen Claude den A-Eintrag, den Sie bei Ihrem DNS-Anbieter anlegen.
4. Claude zeigt **die Dashboard-Adresse, den Benutzernamen und das Passwort**.

> **Speichern Sie das Passwort jetzt.** Es wird nur einmal angezeigt, und OSIR kann es nicht erneut anzeigen. Wenn Sie es verlieren, bitten Sie Claude, es zurückzusetzen.

Die erste Installation dauert einige Minuten, weil der VPS Hermes herunterlädt. Fragen Sie Claude **„Ist mein Hermes bereit?"**, bis er als installiert gemeldet wird.

### Wie verbinden Sie Hermes mit einem KI-Modell?

Hermes braucht einen Modellanbieter, bevor er antworten kann. Sagen Sie zum Beispiel **„Verbinde meinen Hermes mit DeepSeek"** und geben Sie Claude Ihren API-Schlüssel.

- Unterstützte Anbieter: **DeepSeek, OpenAI, OpenRouter und Anthropic**.
- Ihr Schlüssel wird verschlüsselt gespeichert und nie wieder angezeigt. Er steht in keiner Datei, die Sie sehen können.
- Hermes startet mit dem neuen Anbieter neu. Das dauert ein bis zwei Minuten.

Öffnen Sie dann die Dashboard-Adresse, melden Sie sich mit Benutzername und Passwort an und legen Sie los.

### Ist das sicher?

- **Passwortgeschützt:** Das Dashboard verlangt eine Anmeldung. Claude kann das für Sie prüfen: Fragen Sie „Ist mein Hermes-Dashboard geschützt?"
- **HTTPS:** Das Dashboard wird über eine verschlüsselte Verbindung ausgeliefert.
- **Nur Ihr Server:** Hermes läuft auf Ihrem VPS, nicht auf gemeinsam genutzten OSIR-Servern.
- **Einmal-Passwort:** OSIR zeigt das Passwort einmal an und behält keine lesbare Kopie; Ihre API-Schlüssel werden verschlüsselt gespeichert und nie wieder angezeigt. Beide stehen jedoch in Ihrer Claude-Unterhaltung. Wenn andere diesen Chat sehen können, setzen Sie das Passwort danach zurück und ersetzen Sie den Schlüssel.

### Häufige Aufgaben

| Sie möchten | Sagen Sie zu Claude |
|:---|:---|
| Prüfen, ob er läuft | „Ist mein Hermes bereit?" |
| Den KI-Modellanbieter wechseln | „Verbinde meinen Hermes mit OpenAI" |
| Ein neues Passwort bekommen | „Setze mein Hermes-Passwort zurück" |
| Ihre Agenten sehen | „Welche KI-Agenten habe ich?" |
| Hermes entfernen | „Entferne meinen Hermes" |

Einstellungen, die Sie im Hermes-Dashboard ändern, werden ersetzt, wenn Sie das nächste Mal eine Einstellung über Claude ändern. Ihre Unterhaltungen, Fähigkeiten und Erinnerungen bleiben erhalten.

### Was passiert, wenn Sie Hermes entfernen?

Claude bittet Sie zuerst um Bestätigung. Dann wird Hermes auf Ihrem VPS gestoppt und entfernt. Sein Datenordner mit Ihren Unterhaltungen und Erinnerungen bleibt auf dem VPS. Ihr VPS wird **nicht** gekündigt; kündigen Sie ihn separat, wenn Sie ihn nicht mehr brauchen.

### Was kostet das?

Sie zahlen Ihren VPS wie gewohnt. Der KI-Modellanbieter rechnet die Nutzung direkt mit dem Konto ab, dem der verbundene API-Schlüssel gehört.

### Fehlerbehebung

| Claude meldet | Was tun |
|:---|:---|
| Der VPS ist zu klein | Nutzen Sie einen VPS mit mindestens 4 GB RAM |
| Der VPS hat den OSIR-Deploy-Schlüssel abgelehnt | Fügen Sie den von Claude angezeigten Schlüssel in \`/root/.ssh/authorized_keys\` auf dem VPS ein und bitten Sie Claude, es erneut zu versuchen |
| Ein anderes Programm belegt die Ports 80/443 | Stoppen Sie den anderen Webserver auf dem VPS (zum Beispiel nginx) und bitten Sie Claude, es erneut zu versuchen |
| Hermes ist noch nicht verfügbar | Hermes ist bei OSIR noch nicht freigegeben. Es wurde nichts installiert |
| Logs sind noch nicht verfügbar | Fragen Sie stattdessen „Ist mein Hermes bereit?"; Logs Ihres VPS sind über OSIR noch nicht abrufbar |
| Sie haben das Passwort verloren | Sagen Sie „Setze mein Hermes-Passwort zurück" |

Die Installation erneut zu versuchen ist immer sicher: Sie setzt dort fort, wo sie stehen blieb, erzeugt nie ein zweites Passwort und bestellt nie einen zweiten VPS.
`
  }
}

export const LLMS_TXT_LINE =
  '- [Run Hermes Agent on your own VPS](https://osir.com/en/help/run-hermes-agent-on-your-vps/): Install Hermes Agent ' +
  '(Nous Research\'s self-hosted AI agent) on your own OSIR VPS by asking Claude, behind HTTPS with a password-protected ' +
  'dashboard. Needs a VPS with at least 4 GB RAM and an API key from DeepSeek, OpenAI, OpenRouter or Anthropic. Covers ' +
  'install, connecting a model provider, password reset, removal, costs and troubleshooting.'
