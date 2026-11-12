import type { ReactNode } from 'react';

export interface AppShellProps {
  children: ReactNode;
}

export function AppShell({ children }: AppShellProps) {
  return (
    <>
      <header>
        <strong>Axon 5 DCB Courses</strong>
      </header>
      <main data-testid="app-shell">{children}</main>
    </>
  );
}
