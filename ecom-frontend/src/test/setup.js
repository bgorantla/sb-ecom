// Runs before every test file.
import '@testing-library/jest-dom/vitest';   // adds expect(...).toBeInTheDocument() and friends
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Remove whatever the previous test rendered
afterEach(() => cleanup());
