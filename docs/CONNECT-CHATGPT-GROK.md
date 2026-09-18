# Connect OSIR to ChatGPT and Grok

OSIR works in ChatGPT and Grok as a custom MCP connector, so they can search and register domains, manage DNS, order servers and host email for you. You don't need an install or an API key.

|  | ChatGPT | Grok |
| --- | --- | --- |
| Server URL | `https://be.osir.com/mcp/http` (No authentication) or `https://be.osir.com/mcp/oauth` (OAuth) | `https://be.osir.com/mcp/http` |
| Sign-in | In the chat, or OAuth once when you add the connector | In the chat |
| Plans | Plus, Pro, Business, Enterprise, Edu, on the web | Anyone with access to custom connectors on grok.com |

In both apps, anything that costs money or deletes something happens in two steps. The assistant first shows an itemized summary and only goes ahead after you say yes.

## ChatGPT: before you start

ChatGPT connects custom MCP servers through **Developer mode**, on the web version only, with a Plus, Pro, Business, Enterprise or Edu plan ([OpenAI docs](https://developers.openai.com/api/docs/guides/developer-mode)).

1. In ChatGPT on the web, open **Settings → Security and login**.
2. Turn on **Developer mode**.

On Business, Enterprise and Edu plans, a workspace admin may need to allow Developer mode first. ChatGPT labels Developer mode as elevated risk and asks you to confirm write actions by default. OSIR also asks for your confirmation before anything is paid for or deleted.

## ChatGPT Option A: No authentication (recommended)

You paste one URL and log in inside the chat only when a task needs your account.

1. With Developer mode on, open ChatGPT's apps/connectors settings and click **Create** (or the **+** button) to add a developer-mode app.
2. For **Name**, enter `OSIR`. For **Description**, you can enter `Domains, DNS, servers and email from OSIR`.
3. For **MCP server URL**, enter `https://be.osir.com/mcp/http`.
4. For **Authentication**, choose **No authentication**.
5. Confirm that you trust the app, then click **Create**.

**Using it in a chat**

1. Start a new chat, open the **+** menu, choose **Developer mode**, and turn on **OSIR**.
2. Ask something like "Use OSIR to check if coolstartup.io is available."
3. When a task needs your account, ChatGPT gives you a link to `auth.osir.com` and a short code. Approve it in your browser, then tell ChatGPT you're done.

The login lasts for that conversation, up to 8 hours or 30 minutes idle. Say "log me out of OSIR" to end it early.

## ChatGPT Option B: OAuth

You sign in once in your browser, and the app stays signed in for up to 8 hours, or until you've been idle for 30 minutes.

1. With Developer mode on, open the apps/connectors settings and click **Create**.
2. For **Name**, enter `OSIR`.
3. For **MCP server URL**, enter `https://be.osir.com/mcp/oauth`. The URL is different from Option A: it ends in `/mcp/oauth`.
4. For **Authentication**, choose **OAuth**.
5. Open the advanced OAuth settings and set **Client ID** to `mcp-client`. Leave **Client secret** blank.
6. Click **Create**. A browser window opens on `auth.osir.com`.
7. Sign in with your OSIR account and approve.

Don't rely on ChatGPT's automatic client registration. OSIR doesn't support it, so the sign-in fails or the tools return "Forbidden". `mcp-client` is a public ID, not a password, so it's safe to share.

## Grok

Grok's custom connectors take a server URL and can't run a browser sign-in, so Grok always uses the in-chat login ([xAI docs](https://docs.x.ai/grok/connectors)).

1. Go to [grok.com/connectors](https://grok.com/connectors).
2. Click **New Connector**, then choose **Custom**.
3. For the name, enter `OSIR`.
4. For the MCP server URL, enter `https://be.osir.com/mcp/http`.
5. Leave any authentication or header fields empty. OSIR doesn't use API keys.
6. Save the connector.

**Using it in a chat**

1. Start a new chat on grok.com and make sure the OSIR connector is turned on.
2. Ask something like "Use OSIR to check if coolstartup.io is available."
3. When a task needs your account, Grok gives you a link to `auth.osir.com` and a short code. Approve it in your browser, then tell Grok you're done.

On Grok Business and Enterprise, a team admin may have to add the connector first. Don't use the `/mcp/oauth` URL with Grok: it always asks for an OAuth sign-in, which Grok can't do.

## FAQ and troubleshooting

| What you see | Fix |
| --- | --- |
| ChatGPT has no option to add an app | Turn on **Developer mode** in Settings → Security and login. It only works on the web, with Plus, Pro, Business, Enterprise or Edu. |
| The assistant ignores OSIR | Turn the connector on for the chat, and name it in the request: "Use OSIR to…". |
| "Authentication required" | That's normal with No authentication. Let the assistant start the login, approve the code at `auth.osir.com`, then continue. |
| "The provided sessionKey is expired" | The chat's login ran out. Ask the assistant to log you in again. |
| ChatGPT OAuth sign-in fails with a client or redirect error | Edit the app: set Client ID to `mcp-client`, and check the URL ends in `/mcp/oauth`. |
| Tools return "Forbidden" (403) after an OAuth sign-in | The app registered itself automatically. Delete it and create it again with Client ID `mcp-client`. |
| Grok's connector fails to connect | Check the URL is `https://be.osir.com/mcp/http`, not `/mcp/oauth`. |
| A connector that worked stops working | Delete the connector and add it again. Both apps keep settings from when you first added it. |

Still stuck? Contact OSIR support with the time of the error, the app you use and the URL you entered.
