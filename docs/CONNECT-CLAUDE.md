# Connect OSIR to Claude

Add OSIR to Claude as a custom connector and Claude can search and register domains, manage DNS, order servers, host email and deploy sites for you. There are two ways to connect, and both take under two minutes. You don't need an install or a config file, and you don't need an API key.

- **No sign-in**: paste one URL. You log in inside the chat only when a task needs your account.
- **OAuth**: paste a URL and a client ID, and sign in once when you add the connector.

## Which option to choose

We recommend **No sign-in** for most people. Your chat never holds access to your account when you aren't using it.

|  | No sign-in (recommended) | OAuth |
| --- | --- | --- |
| Server URL | `https://be.osir.com/mcp/http` | `https://be.osir.com/mcp/oauth` |
| Authentication setting | No sign-in | Sign in now |
| OAuth client setting | Not shown | Use your own OAuth client, Client ID `mcp-client` |
| When you log in | In the chat, the first time a task needs your account | Once, when you add the connector |
| How long access lasts | One conversation: ends after 30 min idle, 8 h at most, or when you say "log me out" | Ends after 30 min idle or 8 h at most; Claude then asks you to sign in again |
| Works without an OSIR account | Yes: searches, prices and the catalog | No: you sign in before any tool works |
| Best for | Occasional use, shared computers, least standing access | Working across several chats, with no login step in each one |

## Option A: No sign-in

You paste one URL and log in inside the chat when you need to.

1. In Claude, open **Settings → Connectors** and click **Add custom connector**.
2. For **Name**, enter `OSIR`.
3. For **Remote MCP server URL**, enter `https://be.osir.com/mcp/http`.
4. Under **Authentication**, choose **No sign-in**. Claude usually shows it as "Detected" already.
5. Leave **Request headers** empty. OSIR doesn't use API keys.
6. Click **Add**.

Claude warns that anyone with the URL can use the connector. That's expected: without a login, the URL only gives public information such as availability and prices. Anything that touches your account needs you to log in.

**Logging in during a chat**

1. Ask for something that needs your account, such as "What's my OSIR balance?"
2. Claude replies with a link to `auth.osir.com` and a short code.
3. Open the link, check that the code matches, and approve.
4. Tell Claude you're done. It finishes the login and carries on with your request.

The login applies to that conversation only. A new chat asks you to log in again.

## Option B: OAuth

You sign in once in your browser, and the connector stays signed in for up to 8 hours, or until you've been idle for 30 minutes. The URL is different from Option A: it ends in `/mcp/oauth`.

1. In Claude, open **Settings → Connectors** and click **Add custom connector**.
2. For **Name**, enter `OSIR`.
3. For **Remote MCP server URL**, enter `https://be.osir.com/mcp/oauth`.
4. Under **Authentication**, choose **Sign in now**.
5. Under **OAuth client**, choose **Use your own OAuth client**.
6. For **Client ID**, enter `mcp-client`. Leave **Client secret** blank.
7. Leave **Request headers** empty.
8. Click **Add**. A browser window opens on `auth.osir.com`.
9. Sign in with your OSIR account and approve. The connector now shows as connected.

**Don't choose the other OAuth client options.** "Use Claude's published identity" and "Register automatically" don't work with OSIR yet. Claude may pre-select the first one, so change it to **Use your own OAuth client**.

`mcp-client` is a public ID, not a password, so it's safe to share.

## Try it

Start a new chat, make sure the OSIR connector is on, then try these:

| Ask Claude | Needs login |
| --- | --- |
| "Is coolstartup.io available, and what does it cost?" | No |
| "Suggest five short .com names for a coffee brand." | No |
| "What's my OSIR account balance?" | Yes |
| "Register coolstartup.io for two years." | Yes |
| "Point coolstartup.io at 192.0.2.10." | Yes |
| "Spin up a 2 vCPU server in Frankfurt running Ubuntu." | Yes |

Anything that costs money or deletes something happens in two steps. Claude first shows you an itemized summary and only goes ahead after you say yes.

## Signing out and security

- **No sign-in:** say "log me out" in the chat and access ends at once. It also ends by itself after 30 minutes idle, or 8 hours at most.
- **OAuth:** go to **Settings → Connectors**, open OSIR and click **Disconnect**. Do this on any computer other people can use. Until you disconnect, or the sign-in expires after 30 minutes idle or 8 hours, anyone using your Claude account can reach your OSIR account.
- Your OSIR password always goes to `auth.osir.com`, never to Claude. Before you approve a login, check the address bar shows `auth.osir.com`.
- OSIR never asks you for an API key, password or card number in the chat.

## Troubleshooting

| What you see | Fix |
| --- | --- |
| OAuth sign-in fails with an error about the client or registration | Edit the connector: under **OAuth client**, choose **Use your own OAuth client** and enter `mcp-client`. |
| "Authentication required" with the **No sign-in** option | That's normal. Let Claude start the login, approve the code at `auth.osir.com`, then continue. |
| "The provided sessionKey is expired" | The chat's login ran out. Ask Claude to log you in again. |
| Every tool returns 401 on the **No sign-in** option | The URL is wrong. Option A uses `/mcp/http`, not `/mcp/oauth`. |
| Tools return "Forbidden" (403) after an OAuth sign-in | The connector may be using an old registration. Remove it, add it again with `mcp-client`, and sign in again. |
| Claude doesn't use OSIR at all | In the chat, check that the OSIR connector is turned on in the tools menu. |

## FAQ

**Claude says "OSIR is set up as not requiring sign-in, but the server asked for sign-in when checked (status 401)". What do I do?**

Remove the OSIR connector completely, then add it again. Editing the existing one isn't enough, because Claude keeps the result of its first check. Go to **Settings → Connectors**, open OSIR, click **Remove**, then follow [Option A](#option-a-no-sign-in) again with `https://be.osir.com/mcp/http` and **No sign-in**. Also check that the URL ends in `/mcp/http`: `/mcp/oauth` always asks for sign-in.

**Can I switch from one option to the other?**

Yes. Remove the connector, then add it again with the other URL and settings. The two options use different URLs, so changing only the Authentication setting doesn't work.

**Something that used to work stopped working after a change on our side. Why?**

Claude remembers a connector's settings from when you added it. Removing the connector and adding it again makes Claude check the server afresh, and fixes most connection errors.

Still stuck? Contact OSIR support with the time of the error and the option you chose.
