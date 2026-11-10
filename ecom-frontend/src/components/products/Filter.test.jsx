import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Filter from './Filter';

// Shows the current URL, so tests can check what the filter bar wrote into it
function CurrentUrl() {
    const { pathname, search } = useLocation();
    return <output data-testid="url">{pathname + search}</output>;
}

function openProductsPage(url) {
    render(
        <MemoryRouter initialEntries={[url]}>
            <Routes>
                <Route path="/products" element={
                    <>
                        <Filter categories={[{ categoryId: 1, categoryName: 'Kitchen' }]} />
                        <CurrentUrl />
                    </>
                } />
            </Routes>
        </MemoryRouter>
    );
}

const url = () => decodeURIComponent(screen.getByTestId('url').textContent);
const searchBox = () => screen.getByPlaceholderText('Search Products');

describe('Filter', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it('puts the search into the URL once the shopper stops typing', () => {
        openProductsPage('/products?category=Kitchen');

        fireEvent.change(searchBox(), { target: { value: 'mug' } });
        act(() => vi.advanceTimersByTime(500));
        expect(url()).toBe('/products?category=Kitchen');   // still typing, maybe

        act(() => vi.advanceTimersByTime(200));
        expect(url()).toBe('/products?category=Kitchen&keyword=mug');
    });

    it('starts with the search from the URL, e.g. a shared link', () => {
        openProductsPage('/products?keyword=lamp');

        expect(searchBox()).toHaveValue('lamp');
    });

    it('flips the sort order in the URL', () => {
        openProductsPage('/products');

        // The button is labelled by its tooltip, which tells the current order
        fireEvent.click(screen.getByRole('button', { name: 'Sorted by price: asc' }));
        expect(url()).toBe('/products?sortby=desc');

        fireEvent.click(screen.getByRole('button', { name: 'Sorted by price: desc' }));
        expect(url()).toBe('/products?sortby=asc');
    });

    it('Clear Filter empties the URL and the search box', () => {
        openProductsPage('/products?keyword=lamp&sortby=desc&category=Kitchen');

        fireEvent.click(screen.getByRole('button', { name: /clear filter/i }));

        expect(url()).toBe('/products');
        expect(searchBox()).toHaveValue('');
        act(() => vi.advanceTimersByTime(1000));
        expect(url()).toBe('/products');   // the old search doesn't come back
    });
});
