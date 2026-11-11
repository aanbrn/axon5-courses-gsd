import type { ReactNode } from 'react';

export interface AppShellProps {
  children: ReactNode;
}

export function AppShell({ children }: AppShellProps) {
  return (
    <>
      <header>
        <strong>Axon 5 DCB Showcase</strong>
      </header>
      <main data-testid="app-shell">{children}</main>
    </>
  );
}
