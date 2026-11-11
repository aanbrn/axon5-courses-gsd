import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { HomePage } from '@/pages/home';
import './index.css';

const rootElement = document.getElementById('root');

if (!rootElement) {
  throw new Error('index.html is missing the #root mounting element');
}

createRoot(rootElement).render(
  <StrictMode>
    <HomePage />
  </StrictMode>,
);
