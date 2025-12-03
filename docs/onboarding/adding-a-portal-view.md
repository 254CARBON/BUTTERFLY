# Adding a Portal View

> Step-by-step guide to adding a new page or view to perception-portal

**Last Updated**: 2025-12-03  
**Target Audience**: Frontend developers

---

## Overview

The PERCEPTION Portal is a Next.js (App Router) application with React and TypeScript. This guide covers adding new routes, components, and integrating with the PERCEPTION API.

## Prerequisites

- Node.js 20+ installed
- Portal development environment set up
- Familiarity with React, TypeScript, and Next.js App Router

## Architecture Overview

```
perception-portal/
├── src/
│   ├── app/                    # Next.js App Router pages
│   │   ├── layout.tsx          # Root layout
│   │   ├── page.tsx            # Home page
│   │   ├── signals/
│   │   │   ├── page.tsx        # /signals
│   │   │   └── clusters/
│   │   │       ├── page.tsx    # /signals/clusters
│   │   │       └── [clusterId]/
│   │   │           └── page.tsx # /signals/clusters/:id
│   │   └── monitoring/
│   │       └── page.tsx        # /monitoring
│   ├── components/             # Reusable components
│   │   ├── layout/             # Navigation, headers
│   │   ├── signals/            # Signal-related components
│   │   ├── scenarios/          # Scenario components
│   │   └── ui/                 # Shared UI components
│   ├── lib/                    # Utilities and hooks
│   │   ├── api-client.ts       # API client functions
│   │   └── use-*.ts            # Custom hooks
│   ├── types/                  # TypeScript type definitions
│   └── config/                 # Configuration
│       └── api.ts              # API endpoints and config
├── e2e/                        # Playwright E2E tests
└── public/                     # Static assets
```

---

## Step 1: Plan the Route Structure

Decide on the URL structure following existing patterns:

| Route Pattern | Example | Use Case |
|---------------|---------|----------|
| `/feature` | `/signals` | Feature landing page |
| `/feature/list` | `/signals/clusters` | List view |
| `/feature/[id]` | `/signals/clusters/[clusterId]` | Detail view |
| `/feature/[id]/action` | `/scenarios/[id]/edit` | Action page |

---

## Step 2: Create the Page Component

### Basic Page

Create `src/app/myfeature/page.tsx`:

```tsx
'use client';

import { useQuery } from '@tanstack/react-query';
import { getMyFeatureData } from '@/lib/api-client';
import { Loading, Empty, ErrorState } from '@/components/ui/State';

export default function MyFeaturePage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['myfeature'],
    queryFn: getMyFeatureData,
  });

  if (isLoading) return <Loading message="Loading data..." />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;
  if (!data?.length) return <Empty message="No data available" />;

  return (
    <main className="container mx-auto px-4 py-8">
      <header className="mb-8">
        <h1 className="text-3xl font-bold">My Feature</h1>
        <p className="text-gray-600 mt-2">Description of this feature</p>
      </header>
      
      <section className="space-y-4">
        {data.map((item) => (
          <div key={item.id} className="card-surface p-4 rounded-lg">
            <h2 className="text-xl font-semibold">{item.title}</h2>
            <p className="text-gray-600">{item.description}</p>
          </div>
        ))}
      </section>
    </main>
  );
}
```

### Dynamic Route Page

Create `src/app/myfeature/[id]/page.tsx`:

```tsx
'use client';

import { useParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { getMyFeatureDetail } from '@/lib/api-client';
import { Loading, ErrorState } from '@/components/ui/State';

export default function MyFeatureDetailPage() {
  const { id } = useParams<{ id: string }>();
  
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['myfeature', id],
    queryFn: () => getMyFeatureDetail(id),
    enabled: !!id,
  });

  if (isLoading) return <Loading message="Loading details..." />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;
  if (!data) return <ErrorState error="Not found" />;

  return (
    <main className="container mx-auto px-4 py-8">
      <h1 className="text-3xl font-bold">{data.title}</h1>
      {/* Detail content */}
    </main>
  );
}
```

---

## Step 3: Add API Client Functions

Add to `src/lib/api-client.ts`:

```typescript
import { API_BASE_URL, API_ENDPOINTS } from '@/config/api';
import { MyFeatureData, MyFeatureDetail } from '@/types/myfeature';

export async function getMyFeatureData(): Promise<MyFeatureData[]> {
  const response = await fetch(`${API_BASE_URL}${API_ENDPOINTS.MY_FEATURE}`, {
    headers: {
      'Accept': 'application/json',
    },
  });
  
  if (!response.ok) {
    throw new Error(`API error: ${response.status}`);
  }
  
  const envelope = await response.json();
  return envelope.data;
}

export async function getMyFeatureDetail(id: string): Promise<MyFeatureDetail> {
  const response = await fetch(
    `${API_BASE_URL}${API_ENDPOINTS.MY_FEATURE}/${id}`,
    {
      headers: {
        'Accept': 'application/json',
      },
    }
  );
  
  if (!response.ok) {
    throw new Error(`API error: ${response.status}`);
  }
  
  const envelope = await response.json();
  return envelope.data;
}
```

---

## Step 4: Define Types

Create `src/types/myfeature.ts`:

```typescript
export interface MyFeatureData {
  id: string;
  title: string;
  description: string;
  status: MyFeatureStatus;
  createdAt: string;
  updatedAt: string;
}

export interface MyFeatureDetail extends MyFeatureData {
  metadata: Record<string, unknown>;
  related: RelatedItem[];
}

export type MyFeatureStatus = 'active' | 'pending' | 'archived';

export interface RelatedItem {
  id: string;
  type: string;
  label: string;
}
```

---

## Step 5: Add API Endpoint Configuration

Update `src/config/api.ts`:

```typescript
export const API_ENDPOINTS = {
  // ... existing endpoints
  MY_FEATURE: '/api/v1/myfeature',
};
```

---

## Step 6: Create Reusable Components

### Feature-Specific Component

Create `src/components/myfeature/MyFeatureCard.tsx`:

```tsx
import { MyFeatureData } from '@/types/myfeature';
import { StatusPill } from '@/components/ui/StatusPill';

interface MyFeatureCardProps {
  data: MyFeatureData;
  onSelect?: (id: string) => void;
}

export function MyFeatureCard({ data, onSelect }: MyFeatureCardProps) {
  return (
    <article
      className="card-surface p-4 rounded-lg cursor-pointer hover:ring-2 hover:ring-blue-500 transition-all"
      onClick={() => onSelect?.(data.id)}
      data-testid={`feature-card-${data.id}`}
    >
      <header className="flex items-center justify-between mb-2">
        <h3 className="text-lg font-semibold">{data.title}</h3>
        <StatusPill status={data.status} />
      </header>
      <p className="text-gray-600 text-sm">{data.description}</p>
      <footer className="mt-4 text-xs text-gray-400">
        Updated: {new Date(data.updatedAt).toLocaleDateString()}
      </footer>
    </article>
  );
}
```

### Export Components

Create `src/components/myfeature/index.ts`:

```typescript
export { MyFeatureCard } from './MyFeatureCard';
export { MyFeatureList } from './MyFeatureList';
export { MyFeatureDetail } from './MyFeatureDetail';
```

---

## Step 7: Add URL State Management (Optional)

For filterable/sortable views, use URL-driven state:

Create `src/lib/use-myfeature-state.ts`:

```typescript
'use client';

import { useSearchParams, useRouter, usePathname } from 'next/navigation';
import { useCallback, useMemo } from 'react';

interface MyFeatureFilters {
  status: string | null;
  search: string | null;
  sortBy: 'date' | 'name';
}

export function useMyFeatureFilters() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  const filters = useMemo<MyFeatureFilters>(() => ({
    status: searchParams.get('status'),
    search: searchParams.get('q'),
    sortBy: (searchParams.get('sort') as 'date' | 'name') || 'date',
  }), [searchParams]);

  const setFilters = useCallback((updates: Partial<MyFeatureFilters>) => {
    const params = new URLSearchParams(searchParams);
    
    Object.entries(updates).forEach(([key, value]) => {
      if (value === null || value === undefined) {
        params.delete(key);
      } else {
        params.set(key, String(value));
      }
    });
    
    router.push(`${pathname}?${params.toString()}`);
  }, [searchParams, router, pathname]);

  return { filters, setFilters };
}
```

---

## Step 8: Add Navigation Link

Update `src/components/layout/NavBar.tsx`:

```tsx
const navItems = [
  { href: '/', label: 'Home' },
  { href: '/signals', label: 'Signals' },
  { href: '/storylines', label: 'Storylines' },
  { href: '/monitoring', label: 'Monitoring' },
  { href: '/myfeature', label: 'My Feature' },  // Add here
];
```

---

## Step 9: Write Tests

### Unit Test

Create `src/components/myfeature/MyFeatureCard.test.tsx`:

```tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { MyFeatureCard } from './MyFeatureCard';

const mockData = {
  id: 'test-1',
  title: 'Test Feature',
  description: 'Test description',
  status: 'active' as const,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-02T00:00:00Z',
};

describe('MyFeatureCard', () => {
  it('renders feature title and description', () => {
    render(<MyFeatureCard data={mockData} />);
    
    expect(screen.getByText('Test Feature')).toBeInTheDocument();
    expect(screen.getByText('Test description')).toBeInTheDocument();
  });

  it('calls onSelect when clicked', () => {
    const onSelect = vi.fn();
    render(<MyFeatureCard data={mockData} onSelect={onSelect} />);
    
    fireEvent.click(screen.getByTestId('feature-card-test-1'));
    
    expect(onSelect).toHaveBeenCalledWith('test-1');
  });

  it('displays correct status', () => {
    render(<MyFeatureCard data={mockData} />);
    
    expect(screen.getByText('active')).toBeInTheDocument();
  });
});
```

### E2E Test

Create `e2e/myfeature.spec.ts`:

```typescript
import { test, expect } from '@playwright/test';

test.describe('My Feature Page', () => {
  test('should display feature list', async ({ page }) => {
    await page.goto('/myfeature');
    
    // Wait for data to load
    await expect(page.locator('h1')).toContainText('My Feature');
    
    // Verify list renders
    const cards = page.locator('[data-testid^="feature-card-"]');
    await expect(cards.first()).toBeVisible();
  });

  test('should navigate to detail page', async ({ page }) => {
    await page.goto('/myfeature');
    
    // Click first card
    await page.locator('[data-testid^="feature-card-"]').first().click();
    
    // Verify navigation
    await expect(page).toHaveURL(/\/myfeature\/\w+/);
  });

  test('should filter by status', async ({ page }) => {
    await page.goto('/myfeature?status=active');
    
    // Verify URL state preserved
    await expect(page).toHaveURL(/status=active/);
  });
});
```

---

## Step 10: Run and Verify

```bash
# Start development server
cd PERCEPTION/perception-portal
npm run dev

# Run unit tests
npm run test

# Run E2E tests
npm run test:e2e

# Build for production
npm run build
```

---

## Styling Guidelines

### Use Existing CSS Classes

```css
/* Available utility classes */
.card-surface  /* Standard card background with border */
.glass         /* Glass morphism effect */
```

### Follow Tailwind Patterns

```tsx
// Good: Use consistent spacing
<div className="space-y-4">
<div className="px-4 py-8">

// Good: Use responsive prefixes
<div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3">

// Good: Use color semantics
<span className="text-gray-600">  {/* Secondary text */}
<span className="text-red-500">   {/* Error */}
<span className="text-green-500"> {/* Success */}
```

---

## Checklist

Before submitting your PR:

- [ ] Page follows App Router conventions (`'use client'` directive)
- [ ] Types defined in `src/types/`
- [ ] API client functions added with proper error handling
- [ ] Components use existing UI primitives (`State.tsx`, `StatusPill.tsx`)
- [ ] URL state for filters/sorts (if applicable)
- [ ] Navigation link added
- [ ] Unit tests with >80% coverage
- [ ] E2E tests for key flows
- [ ] Responsive design tested
- [ ] Loading, empty, and error states handled
- [ ] Accessibility: semantic HTML, ARIA labels

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Portal README](../../PERCEPTION/perception-portal/README.md) | Portal architecture |
| [API Client](../../PERCEPTION/perception-portal/src/lib/api-client.ts) | API integration patterns |
| [E2E Debugging](../../PERCEPTION/docs/runbooks/portal-e2e-debugging.md) | Test debugging guide |
| [Next.js App Router](https://nextjs.org/docs/app) | Next.js documentation |

