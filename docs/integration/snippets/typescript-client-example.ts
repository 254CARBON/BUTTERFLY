/**
 * PERCEPTION API Integration - TypeScript Examples
 *
 * Prerequisites:
 *   npm install @z254/perception-client
 *
 * Or from source:
 *   cd PERCEPTION/clients/typescript
 *   npm install && npm run build
 *   npm link
 */

import {
  PerceptionClient,
  PerceptionConfig,
  Source,
  SourceType,
  Event,
  WeakSignal,
  RimNode,
  CreateSourceRequest,
  ScenarioFeedbackRequest,
  Outcome,
  PerceptionNotFoundError,
  PerceptionUnauthorizedError,
  PerceptionRateLimitedError,
  PerceptionApiError,
} from "@z254/perception-client";

// =============================================================================
// Client Initialization
// =============================================================================

function createClient(): PerceptionClient {
  const config: PerceptionConfig = {
    baseUrl: process.env.PERCEPTION_BASE_URL || "http://localhost:8080",
    apiKey: process.env.PERCEPTION_API_KEY,
    timeout: 30000,
    retryOnFailure: true,
    maxRetries: 3,
  };

  return new PerceptionClient(config);
}

// =============================================================================
// Authentication
// =============================================================================

async function authenticate(client: PerceptionClient): Promise<string> {
  const response = await client.auth.exchangeApiKey(
    process.env.PERCEPTION_API_KEY!
  );
  return response.token;
}

// =============================================================================
// Acquisition API - Managing Data Sources
// =============================================================================

async function registerRssSource(client: PerceptionClient): Promise<Source> {
  const request: CreateSourceRequest = {
    name: "Tech News Feed",
    type: SourceType.RSS_FEED,
    endpoint: "https://example.com/rss/tech.xml",
    schedule: "0 */30 * * * *", // Every 30 minutes
    tenantId: "global",
    rateLimit: 60,
  };

  return client.acquisition.createSource(request);
}

async function listSources(client: PerceptionClient): Promise<Source[]> {
  return client.acquisition.listSources({ page: 0, size: 20 });
}

async function triggerAcquisition(
  client: PerceptionClient,
  sourceId: string
): Promise<void> {
  await client.acquisition.triggerSource(sourceId);
}

// =============================================================================
// Intelligence API - Events and Scenarios
// =============================================================================

async function getRecentEvents(
  client: PerceptionClient,
  limit: number = 20
): Promise<Event[]> {
  return client.intelligence.listEvents({ limit, includeScenarios: true });
}

async function getEventDetails(
  client: PerceptionClient,
  eventId: string
): Promise<Event> {
  return client.intelligence.getEvent(eventId);
}

async function submitScenarioFeedback(
  client: PerceptionClient,
  scenarioId: string,
  success: boolean,
  notes: string
): Promise<void> {
  const request: ScenarioFeedbackRequest = {
    feedback: success
      ? "Scenario executed successfully"
      : "Scenario execution failed",
    outcome: success ? Outcome.SUCCESS : Outcome.FAILURE,
    notes,
  };

  await client.intelligence.submitFeedback(scenarioId, request);
}

// =============================================================================
// Signals API - Weak Signal Detection
// =============================================================================

async function getDetectedSignals(
  client: PerceptionClient,
  hoursBack: number = 24
): Promise<WeakSignal[]> {
  return client.signals.getDetectedSignals({ hoursBack });
}

async function trackEventHorizon(
  client: PerceptionClient,
  topic: string
): Promise<any> {
  return client.signals.trackHorizon(topic);
}

async function getSignalClusters(client: PerceptionClient): Promise<any> {
  return client.signals.getClusters({
    timeWindowHours: 24,
    minClusterSize: 2,
  });
}

async function getCoOccurrences(
  client: PerceptionClient,
  topicThreshold: number = 0.5
): Promise<any> {
  return client.signals.getCoOccurrences({ topicThreshold });
}

// =============================================================================
// RIM API - Reality Integration Mesh
// =============================================================================

async function registerRimNode(client: PerceptionClient): Promise<RimNode> {
  return client.rim.createNode({
    nodeId: "rim:entity:finance:EURUSD",
    displayName: "EUR/USD Currency Pair",
    nodeType: "entity",
    namespace: "finance",
    region: "global",
    domain: "fx",
    status: "ACTIVE",
  });
}

async function getMeshSnapshot(
  client: PerceptionClient,
  scope: string = "global",
  windowMinutes: number = 5
): Promise<any> {
  return client.rim.getMesh({ scope, window: `PT${windowMinutes}M` });
}

async function getAnomalies(
  client: PerceptionClient,
  minSeverity: number = 0.5
): Promise<any> {
  return client.rim.getAnomalies({ minSeverity });
}

async function getIgnoranceSurface(client: PerceptionClient): Promise<any> {
  return client.rim.getIgnoranceSurface();
}

// =============================================================================
// Integrity API - Trust Scoring
// =============================================================================

async function getContentTrustScore(
  client: PerceptionClient,
  contentId: string
): Promise<any> {
  return client.integrity.getContentTrust(contentId);
}

async function getSourceTrustScore(
  client: PerceptionClient,
  sourceId: string
): Promise<any> {
  return client.integrity.getSourceTrust(sourceId);
}

// =============================================================================
// Stream A API - Reasoning & Predictions
// =============================================================================

async function applyReasoning(
  client: PerceptionClient,
  interpretation: string,
  initialPlausibility: number
): Promise<any> {
  return client.reasoning.apply({
    interpretation,
    initialPlausibility,
  });
}

async function checkPlausibility(
  client: PerceptionClient,
  interpretation: string
): Promise<any> {
  return client.reasoning.checkPlausibility({ interpretation });
}

async function generatePrediction(
  client: PerceptionClient,
  target: string,
  currentValue: number,
  horizonHours: number = 24
): Promise<any> {
  return client.predictive.predict({
    target,
    currentValue,
    horizonHours,
  });
}

async function getEarlyWarnings(client: PerceptionClient): Promise<any> {
  return client.predictive.getEarlyWarnings();
}

// =============================================================================
// Parallel Operations
// =============================================================================

async function parallelFetch(client: PerceptionClient): Promise<{
  events: Event[];
  signals: WeakSignal[];
  mesh: any;
}> {
  const [events, signals, mesh] = await Promise.all([
    client.intelligence.listEvents({ limit: 10 }),
    client.signals.getDetectedSignals({ hoursBack: 24 }),
    client.rim.getMesh({ scope: "global", window: "PT5M" }),
  ]);

  return { events, signals, mesh };
}

// =============================================================================
// Error Handling
// =============================================================================

async function exampleWithErrorHandling(
  client: PerceptionClient,
  eventId: string
): Promise<Event | null> {
  try {
    const event = await client.intelligence.getEvent(eventId);
    console.log(`Event: ${event.title}`);
    return event;
  } catch (error) {
    if (error instanceof PerceptionNotFoundError) {
      console.error(`Event not found: ${eventId}`);
      return null;
    }
    if (error instanceof PerceptionUnauthorizedError) {
      console.error("Authentication failed - check API key");
      return null;
    }
    if (error instanceof PerceptionRateLimitedError) {
      console.error(`Rate limited - retry after: ${error.retryAfter}`);
      return null;
    }
    if (error instanceof PerceptionApiError) {
      console.error(`API error: ${error.errorCode} - ${error.message}`);
      return null;
    }
    throw error;
  }
}

// =============================================================================
// Pagination Helper
// =============================================================================

async function* paginateAllEvents(
  client: PerceptionClient,
  pageSize: number = 50
): AsyncGenerator<Event> {
  let page = 0;
  while (true) {
    const events = await client.intelligence.listEvents({
      page,
      size: pageSize,
    });
    if (events.length === 0) break;
    for (const event of events) {
      yield event;
    }
    page++;
  }
}

// =============================================================================
// React Hook Example
// =============================================================================

/*
 * Example React hook for using PERCEPTION client:
 *
 * import { useState, useEffect } from 'react';
 * import { PerceptionClient, Event } from '@z254/perception-client';
 *
 * export function usePerceptionEvents(limit: number = 10) {
 *   const [events, setEvents] = useState<Event[]>([]);
 *   const [loading, setLoading] = useState(true);
 *   const [error, setError] = useState<Error | null>(null);
 *
 *   useEffect(() => {
 *     const client = new PerceptionClient({
 *       baseUrl: process.env.REACT_APP_PERCEPTION_URL!,
 *       apiKey: process.env.REACT_APP_PERCEPTION_API_KEY!,
 *     });
 *
 *     client.intelligence.listEvents({ limit })
 *       .then(setEvents)
 *       .catch(setError)
 *       .finally(() => setLoading(false));
 *
 *     return () => client.close();
 *   }, [limit]);
 *
 *   return { events, loading, error };
 * }
 */

// =============================================================================
// Main - Demo
// =============================================================================

async function main(): Promise<void> {
  const client = createClient();

  try {
    // List events
    const events = await getRecentEvents(client);
    console.log(`Found ${events.length} events`);

    // Get weak signals
    const signals = await getDetectedSignals(client);
    console.log(`Detected ${signals.length} weak signals`);

    // Register source
    try {
      const source = await registerRssSource(client);
      console.log(`Registered source: ${source.id}`);
    } catch (error) {
      if (error instanceof PerceptionApiError) {
        console.error(`Failed to register source: ${error.message}`);
      } else {
        throw error;
      }
    }

    // Get mesh snapshot
    const mesh = await getMeshSnapshot(client);
    console.log(`Mesh has ${mesh.nodes.length} nodes`);

    // Apply reasoning
    const result = await applyReasoning(
      client,
      "Market rose after positive earnings",
      0.8
    );
    console.log(`Reasoning result: plausibility=${result.finalPlausibility}`);

    // Parallel fetch example
    const data = await parallelFetch(client);
    console.log(
      `Parallel fetch: ${data.events.length} events, ${data.signals.length} signals`
    );
  } finally {
    await client.close();
  }
}

// Run main
main().catch(console.error);
