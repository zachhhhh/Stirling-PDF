(function () {
  const licenseContainer = document.getElementById('license-status');
  const usageContainer = document.getElementById('usage-metrics');

  if (!licenseContainer || !usageContainer) {
    return;
  }

  const numberFormatter = new Intl.NumberFormat();
  const dateFormatter = new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });

  function setError(container) {
    const message = container.dataset.labelError || 'Unable to load data';
    container.innerHTML = `<div class="data-error">${message}</div>`;
  }

  function renderDefinitionList(container, rows) {
    const dl = document.createElement('dl');
    dl.className = 'data-definition-list';

    rows.forEach(({ label, value }) => {
      if (value === undefined || value === null) {
        return;
      }
      const dt = document.createElement('dt');
      dt.textContent = label;
      const dd = document.createElement('dd');
      if (value instanceof HTMLElement) {
        dd.appendChild(value);
      } else {
        dd.textContent = value;
      }
      dl.appendChild(dt);
      dl.appendChild(dd);
    });

    container.replaceChildren(dl);
  }

  function formatPlan(status) {
    if (!status.licenseConfigured) {
      return licenseContainer.dataset.labelUnconfigured || 'Community (not configured)';
    }
    if (!status.level) {
      return 'N/A';
    }
    const lower = status.level.toLowerCase();
    return lower.charAt(0).toUpperCase() + lower.slice(1);
  }

  function formatVerification(status) {
    if (!status.licenseConfigured) {
      return licenseContainer.dataset.labelUnconfigured || 'Community (not configured)';
    }
    if (status.licenseVerified) {
      return licenseContainer.dataset.labelVerified || 'Verified';
    }
    return licenseContainer.dataset.labelUnverified || 'Awaiting verification';
  }

  function formatFeatures(features) {
    if (!Array.isArray(features) || features.length === 0) {
      return licenseContainer.dataset.labelNone || 'No premium features enabled yet';
    }
    const list = document.createElement('div');
    list.className = 'data-feature-stack';
    features.forEach((feature) => {
      const badge = document.createElement('span');
      badge.className = 'data-badge';
      badge.textContent = beautifyToken(feature);
      list.appendChild(badge);
    });
    return list;
  }

  function beautifyToken(token) {
    if (!token) {
      return token;
    }
    return token
      .toLowerCase()
      .split(/[_\s]+/)
      .filter(Boolean)
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }

  function renderLicense(status) {
    const rows = [
      {
        label: licenseContainer.dataset.labelPlan || 'Plan',
        value: formatPlan(status),
      },
      {
        label: licenseContainer.dataset.labelVerification || 'Verification',
        value: formatVerification(status),
      },
      {
        label: licenseContainer.dataset.labelUsers || 'Licensed Users',
        value: status.maxUsers > 0 ? numberFormatter.format(status.maxUsers) : 'N/A',
      },
      {
        label: licenseContainer.dataset.labelFeatures || 'Enabled Features',
        value: formatFeatures(status.enabledFeatures),
      },
    ];

    if (status.expiresAt) {
      const expires = new Date(status.expiresAt);
      rows.push({
        label: licenseContainer.dataset.labelExpires || 'Expires',
        value: dateFormatter.format(expires),
      });
    }

    renderDefinitionList(licenseContainer, rows);
  }

  function renderUsage(snapshot) {
    if (!snapshot) {
      setError(usageContainer);
      return;
    }

    const rows = [
      {
        label: usageContainer.dataset.labelTotalOperations || 'Total Operations',
        value: numberFormatter.format(snapshot.totalOperations || 0),
      },
      {
        label: usageContainer.dataset.labelTotalFiles || 'Files Processed',
        value: numberFormatter.format(snapshot.totalFilesProcessed || 0),
      },
      {
        label: usageContainer.dataset.labelGenerated || 'Snapshot Generated',
        value: snapshot.generatedAt
          ? dateFormatter.format(new Date(snapshot.generatedAt))
          : 'N/A',
      },
    ];

    const entries = Array.isArray(snapshot.entries) ? snapshot.entries.slice(0, 5) : [];

    renderDefinitionList(usageContainer, rows);

    const title = document.createElement('h4');
    title.textContent = usageContainer.dataset.labelTopOperations || 'Top Operations';

    const list = document.createElement('ol');
    list.className = 'data-list';

    if (entries.length === 0) {
      const empty = document.createElement('div');
      empty.className = 'data-muted';
      empty.textContent = usageContainer.dataset.labelNone || 'No activity recorded yet';
      usageContainer.appendChild(title);
      usageContainer.appendChild(empty);
      return;
    }

    entries.forEach((entry) => {
      const item = document.createElement('li');
      const filesText = numberFormatter.format(entry.filesProcessed || 0);
      const invocationsText = numberFormatter.format(entry.invocations || 0);
      const label = beautifyToken(entry.operation || 'Unknown');
      item.textContent = `${label} — ${invocationsText} × (${filesText} files)`;
      list.appendChild(item);
    });

    usageContainer.appendChild(title);
    usageContainer.appendChild(list);
  }

  function fetchJson(url) {
    return fetch(url, {
      credentials: 'same-origin',
      headers: {
        Accept: 'application/json',
      },
    }).then((response) => {
      if (!response.ok) {
        throw new Error(`Request failed: ${response.status}`);
      }
      return response.json();
    });
  }

  function init() {
    fetchJson('/api/v1/admin/license/status')
      .then(renderLicense)
      .catch(() => setError(licenseContainer));

    fetchJson('/api/v1/admin/usage/snapshot')
      .then(renderUsage)
      .catch(() => setError(usageContainer));
  }

  document.addEventListener('DOMContentLoaded', init);
})();
