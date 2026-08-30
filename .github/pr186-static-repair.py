from pathlib import Path


def replace_once(path, old, new):
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, found {count}")
    file.write_text(text.replace(old, new, 1))


replace_once(
    "staff-bot/src/main/java/net/enthusia/staff/discordbot/ModerationPreviewWebConfig.java",
    '''    private static InetSocketAddress parseBind(String value) {
        HostAndPort parsed = parseHostAndPort(value);
        return new InetSocketAddress(loopbackAddress(parsed.host()), parsed.port());
    }
''',
    '''    private static InetSocketAddress parseBind(String value) {
        HostAndPort parsed = parseHostAndPort(value);
        if (IPV4_LOOPBACK_HOST.equals(parsed.host()) || LOCALHOST.equalsIgnoreCase(parsed.host())) {
            return new InetSocketAddress(IPV4_LOOPBACK, parsed.port());
        }
        if (IPV6_LOOPBACK_HOST.equals(parsed.host())) {
            return new InetSocketAddress(IPV6_LOOPBACK, parsed.port());
        }
        throw new IllegalArgumentException("preview web bind must use an explicit loopback host");
    }
''')

replace_once(
    "staff-bot/src/main/java/net/enthusia/staff/discordbot/ModerationPreviewWebConfig.java",
    '''    private static InetAddress loopbackAddress(String host) {
        if (IPV4_LOOPBACK_HOST.equals(host) || LOCALHOST.equalsIgnoreCase(host)) {
            return IPV4_LOOPBACK;
        }
        if (IPV6_LOOPBACK_HOST.equals(host)) {
            return IPV6_LOOPBACK;
        }
        throw new IllegalArgumentException("preview web bind must use an explicit loopback host");
    }

''',
    '')

replace_once(
    "staff-bot/src/main/java/net/enthusia/staff/discordbot/ModerationPreviewWebRuntime.java",
    '''            stopResources(server, executor);
            server = null;
            executor = null;
''',
    '''            stopResources(server, executor);
''')

replace_once(
    "staff-bot/src/test/java/net/enthusia/staff/discordbot/ModerationPreviewWebRuntimeTest.java",
    '        var startFailure = new AtomicReference<Throwable>();\n',
    '        var startFailure = new AtomicReference<RuntimeException>();\n')
replace_once(
    "staff-bot/src/test/java/net/enthusia/staff/discordbot/ModerationPreviewWebRuntimeTest.java",
    '        Throwable failure = startFailure.get();\n',
    '        RuntimeException failure = startFailure.get();\n')
replace_once(
    "staff-bot/src/test/java/net/enthusia/staff/discordbot/ModerationPreviewWebRuntimeTest.java",
    '            AtomicReference<Throwable> failure\n',
    '            AtomicReference<RuntimeException> failure\n')
replace_once(
    "staff-bot/src/test/java/net/enthusia/staff/discordbot/ModerationPreviewWebRuntimeTest.java",
    '''        } catch (Throwable throwable) {
            failure.set(throwable);
''',
    '''        } catch (RuntimeException exception) {
            failure.set(exception);
''')

replace_once(
    "staff-bot/src/main/resources/moderation-preview/app.js",
    '''function messageMatchesFilters(message, term, contextIds) {
  if (contextIds && !contextIds.has(message.id)) return false;
  if (term && !messageSearchText(message).includes(term)) return false;
  if (state.channel !== 'all' && message.channel !== state.channel) return false;
  if (state.date !== 'all' && messageDateKey(message.time) !== state.date) return false;
  return !state.selectedOnly || state.selected.has(message.id);
}
''',
    '''function messageMatchesFilters(message, term, contextIds) {
  const checks = [
    matchesContext(message, contextIds),
    matchesSearchTerm(message, term),
    matchesChannel(message),
    matchesDate(message),
    matchesSelection(message)
  ];
  return checks.every(Boolean);
}

function matchesContext(message, contextIds) {
  return contextIds === null || contextIds.has(message.id);
}

function matchesSearchTerm(message, term) {
  return term === '' || messageSearchText(message).includes(term);
}

function matchesChannel(message) {
  return state.channel === 'all' || message.channel === state.channel;
}

function matchesDate(message) {
  return state.date === 'all' || messageDateKey(message.time) === state.date;
}

function matchesSelection(message) {
  return !state.selectedOnly || state.selected.has(message.id);
}
''')

replace_once(
    "staff-bot/src/main/resources/moderation-preview/model.js",
    '''function element(tag, options = {}, ...children) {
  const created = document.createElement(tag);
  if (options.className) created.className = options.className;
  if (options.text !== undefined) created.textContent = String(options.text);
  if (options.type) created.type = options.type;
  if (options.id) created.id = options.id;
  if (options.value !== undefined) created.value = String(options.value);
  if (options.placeholder) created.placeholder = options.placeholder;
  if (options.name) created.name = options.name;
  if (options.htmlFor) created.htmlFor = options.htmlFor;
  if (options.checked !== undefined) created.checked = Boolean(options.checked);
  if (options.disabled !== undefined) created.disabled = Boolean(options.disabled);
  if (options.hidden !== undefined) created.hidden = Boolean(options.hidden);
  if (options.attrs) {
    for (const [name, value] of Object.entries(options.attrs)) created.setAttribute(name, String(value));
  }
  if (options.dataset) {
    for (const [name, value] of Object.entries(options.dataset)) created.dataset[name] = String(value);
  }
  appendChildren(created, children);
  return created;
}
''',
    '''function element(tag, options = {}, ...children) {
  const created = document.createElement(tag);
  applyElementTextAndValue(created, options);
  applyElementScalarProperties(created, options);
  applyElementBooleanProperties(created, options);
  applyElementMaps(created, options);
  appendChildren(created, children);
  return created;
}

function applyElementTextAndValue(created, options) {
  if (options.text !== undefined) created.textContent = String(options.text);
  if (options.value !== undefined) created.value = String(options.value);
}

function applyElementScalarProperties(created, options) {
  if (options.className) created.className = options.className;
  if (options.type) created.type = options.type;
  if (options.id) created.id = options.id;
  if (options.placeholder) created.placeholder = options.placeholder;
  if (options.name) created.name = options.name;
  if (options.htmlFor) created.htmlFor = options.htmlFor;
}

function applyElementBooleanProperties(created, options) {
  if (options.checked !== undefined) created.checked = Boolean(options.checked);
  if (options.disabled !== undefined) created.disabled = Boolean(options.disabled);
  if (options.hidden !== undefined) created.hidden = Boolean(options.hidden);
}

function applyElementMaps(created, options) {
  applyAttributes(created, options.attrs);
  applyDataset(created, options.dataset);
}

function applyAttributes(created, attrs) {
  if (!attrs) return;
  for (const [name, value] of Object.entries(attrs)) created.setAttribute(name, String(value));
}

function applyDataset(created, dataset) {
  if (!dataset) return;
  for (const [name, value] of Object.entries(dataset)) created.dataset[name] = String(value);
}
''')

replace_once(
    "staff-bot/src/main/resources/moderation-preview/workflow.js",
    "  $('[data-offense]')?.focus();\n",
    "  const firstOffense = $('[data-offense]');\n  if (firstOffense) firstOffense.focus();\n")
replace_once(
    "staff-bot/src/main/resources/moderation-preview/workflow.js",
    '''function workflowStepNode(label, index, current) {
  const classes = ['workflow-step', index === current ? 'active' : '', index < current ? 'done' : ''].filter(Boolean).join(' ');
  return element('div', {className: classes}, element('span', {text: index + 1}), label);
}
''',
    '''function workflowStepNode(label, index, current) {
  return element('div', {className: workflowStepClass(index, current)},
    element('span', {text: index + 1}), label);
}

function workflowStepClass(index, current) {
  if (index === current) return 'workflow-step active';
  if (index < current) return 'workflow-step done';
  return 'workflow-step';
}
''')
replace_once(
    "staff-bot/src/main/resources/moderation-preview/workflow.js",
    "  const label = OFFENSES.find(([value]) => value === key)?.[1] || 'Other';\n",
    "  const label = offenseLabel(key);\n")
replace_once(
    "staff-bot/src/main/resources/moderation-preview/workflow.js",
    'function recommend(key) {\n',
    '''function offenseLabel(key) {
  const match = OFFENSES.find((entry) => entry[0] === key);
  return match ? match[1] : 'Other';
}

function recommend(key) {
''')
replace_once(
    "staff-bot/src/main/resources/moderation-preview/workflow.js",
    "  const offense = OFFENSES.find(([value]) => value === key)?.[1] || 'Other';\n",
    "  const offense = offenseLabel(key);\n")
