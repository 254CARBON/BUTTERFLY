#!/usr/bin/env python3
"""
PERCEPTION API Integration - Python Examples

Prerequisites:
    pip install perception-client
    
Or from source:
    cd PERCEPTION/clients/python
    pip install -e .
"""

import os
import asyncio
from datetime import datetime, timedelta
from typing import Optional

from perception_client import PerceptionClient, PerceptionConfig
from perception_client.models import (
    Source, SourceType, Event, WeakSignal, RimNode, 
    CreateSourceRequest, ScenarioFeedbackRequest, Outcome
)
from perception_client.exceptions import (
    PerceptionNotFoundError, 
    PerceptionUnauthorizedError,
    PerceptionRateLimitedError,
    PerceptionApiError
)


def create_client() -> PerceptionClient:
    """Initialize the PERCEPTION client with configuration."""
    config = PerceptionConfig(
        base_url=os.getenv("PERCEPTION_BASE_URL", "http://localhost:8080"),
        api_key=os.getenv("PERCEPTION_API_KEY"),
        timeout=30,
        retry_on_failure=True,
        max_retries=3,
    )
    return PerceptionClient(config)


# =============================================================================
# Authentication
# =============================================================================

def authenticate(client: PerceptionClient) -> str:
    """Exchange API key for JWT token (if using API key auth)."""
    response = client.auth.exchange_api_key(os.getenv("PERCEPTION_API_KEY"))
    return response.token


# =============================================================================
# Acquisition API - Managing Data Sources
# =============================================================================

def register_rss_source(client: PerceptionClient) -> Source:
    """Register a new RSS feed data source."""
    request = CreateSourceRequest(
        name="Tech News Feed",
        type=SourceType.RSS_FEED,
        endpoint="https://example.com/rss/tech.xml",
        schedule="0 */30 * * * *",  # Every 30 minutes
        tenant_id="global",
        rate_limit=60,
    )
    return client.acquisition.create_source(request)


def list_sources(client: PerceptionClient) -> list[Source]:
    """List all registered sources."""
    return client.acquisition.list_sources(page=0, size=20)


def trigger_acquisition(client: PerceptionClient, source_id: str) -> None:
    """Trigger manual acquisition for a source."""
    client.acquisition.trigger_source(source_id)


# =============================================================================
# Intelligence API - Events and Scenarios
# =============================================================================

def get_recent_events(client: PerceptionClient, limit: int = 20) -> list[Event]:
    """Get recent events with scenarios."""
    return client.intelligence.list_events(limit=limit, include_scenarios=True)


def get_event_details(client: PerceptionClient, event_id: str) -> Event:
    """Get event details with full scenario bundle."""
    return client.intelligence.get_event(event_id)


def submit_scenario_feedback(
    client: PerceptionClient, 
    scenario_id: str, 
    success: bool, 
    notes: str
) -> None:
    """Submit feedback on scenario execution."""
    request = ScenarioFeedbackRequest(
        feedback="Scenario executed successfully" if success else "Scenario execution failed",
        outcome=Outcome.SUCCESS if success else Outcome.FAILURE,
        notes=notes,
    )
    client.intelligence.submit_feedback(scenario_id, request)


# =============================================================================
# Signals API - Weak Signal Detection
# =============================================================================

def get_detected_signals(client: PerceptionClient, hours_back: int = 24) -> list[WeakSignal]:
    """Get detected weak signals from the specified time window."""
    return client.signals.get_detected_signals(hours_back=hours_back)


def track_event_horizon(client: PerceptionClient, topic: str):
    """Track an event horizon for a topic."""
    return client.signals.track_horizon(topic)


def get_signal_clusters(client: PerceptionClient):
    """Get signal clusters."""
    return client.signals.get_clusters(time_window_hours=24, min_cluster_size=2)


def get_co_occurrences(client: PerceptionClient, topic_threshold: float = 0.5):
    """Get topic co-occurrence insights."""
    return client.signals.get_co_occurrences(topic_threshold=topic_threshold)


# =============================================================================
# RIM API - Reality Integration Mesh
# =============================================================================

def register_rim_node(client: PerceptionClient) -> RimNode:
    """Register a new RIM node."""
    return client.rim.create_node(
        node_id="rim:entity:finance:EURUSD",
        display_name="EUR/USD Currency Pair",
        node_type="entity",
        namespace="finance",
        region="global",
        domain="fx",
        status="ACTIVE",
    )


def get_mesh_snapshot(client: PerceptionClient, scope: str = "global", window_minutes: int = 5):
    """Get mesh snapshot."""
    return client.rim.get_mesh(scope=scope, window=f"PT{window_minutes}M")


def get_anomalies(client: PerceptionClient, min_severity: float = 0.5):
    """Get active anomalies."""
    return client.rim.get_anomalies(min_severity=min_severity)


def get_ignorance_surface(client: PerceptionClient):
    """Get the ignorance surface (areas of low certainty)."""
    return client.rim.get_ignorance_surface()


# =============================================================================
# Integrity API - Trust Scoring
# =============================================================================

def get_content_trust_score(client: PerceptionClient, content_id: str):
    """Get trust score for content."""
    return client.integrity.get_content_trust(content_id)


def get_source_trust_score(client: PerceptionClient, source_id: str):
    """Get trust score for a source."""
    return client.integrity.get_source_trust(source_id)


# =============================================================================
# Stream A API - Reasoning & Predictions
# =============================================================================

def apply_reasoning(
    client: PerceptionClient, 
    interpretation: str, 
    initial_plausibility: float
):
    """Apply reasoning rules to an interpretation."""
    return client.reasoning.apply(
        interpretation=interpretation,
        initial_plausibility=initial_plausibility,
    )


def check_plausibility(client: PerceptionClient, interpretation: str):
    """Check plausibility of an interpretation."""
    return client.reasoning.check_plausibility(interpretation=interpretation)


def generate_prediction(
    client: PerceptionClient,
    target: str,
    current_value: float,
    horizon_hours: int = 24,
):
    """Generate prediction for a target metric."""
    return client.predictive.predict(
        target=target,
        current_value=current_value,
        horizon_hours=horizon_hours,
    )


def get_early_warnings(client: PerceptionClient):
    """Get early warnings from predictive models."""
    return client.predictive.get_early_warnings()


# =============================================================================
# Async Operations
# =============================================================================

async def get_events_async(client: PerceptionClient, limit: int = 20):
    """Async event fetch."""
    return await client.intelligence.list_events_async(limit=limit)


async def parallel_fetch_example(client: PerceptionClient):
    """Fetch multiple resources in parallel."""
    events_task = client.intelligence.list_events_async(limit=10)
    signals_task = client.signals.get_detected_signals_async(hours_back=24)
    mesh_task = client.rim.get_mesh_async(scope="global", window="PT5M")
    
    events, signals, mesh = await asyncio.gather(
        events_task, signals_task, mesh_task
    )
    
    return {
        "events": events,
        "signals": signals,
        "mesh": mesh,
    }


# =============================================================================
# Error Handling
# =============================================================================

def example_with_error_handling(client: PerceptionClient, event_id: str) -> Optional[Event]:
    """Example with proper error handling."""
    try:
        event = client.intelligence.get_event(event_id)
        print(f"Event: {event.title}")
        return event
    except PerceptionNotFoundError:
        print(f"Event not found: {event_id}")
        return None
    except PerceptionUnauthorizedError:
        print("Authentication failed - check API key")
        return None
    except PerceptionRateLimitedError as e:
        print(f"Rate limited - retry after: {e.retry_after}")
        return None
    except PerceptionApiError as e:
        print(f"API error: {e.error_code} - {e.message}")
        return None


# =============================================================================
# Context Manager Usage
# =============================================================================

def context_manager_example():
    """Using client as a context manager for automatic cleanup."""
    config = PerceptionConfig(
        base_url="http://localhost:8080",
        api_key=os.getenv("PERCEPTION_API_KEY"),
    )
    
    with PerceptionClient(config) as client:
        events = client.intelligence.list_events(limit=10)
        print(f"Found {len(events)} events")


# =============================================================================
# Pagination Helper
# =============================================================================

def paginate_all_events(client: PerceptionClient, page_size: int = 50):
    """Generator to paginate through all events."""
    page = 0
    while True:
        events = client.intelligence.list_events(page=page, size=page_size)
        if not events:
            break
        yield from events
        page += 1


# =============================================================================
# Main - Demo
# =============================================================================

def main():
    """Demo of PERCEPTION Python client."""
    client = create_client()
    
    # List events
    events = get_recent_events(client)
    print(f"Found {len(events)} events")
    
    # Get weak signals
    signals = get_detected_signals(client)
    print(f"Detected {len(signals)} weak signals")
    
    # Register source
    try:
        source = register_rss_source(client)
        print(f"Registered source: {source.id}")
    except PerceptionApiError as e:
        print(f"Failed to register source: {e}")
    
    # Get mesh snapshot
    mesh = get_mesh_snapshot(client)
    print(f"Mesh has {len(mesh.nodes)} nodes")
    
    # Apply reasoning
    result = apply_reasoning(
        client,
        "Market rose after positive earnings",
        initial_plausibility=0.8,
    )
    print(f"Reasoning result: plausibility={result.final_plausibility}")


if __name__ == "__main__":
    main()
