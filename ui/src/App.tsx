import { useMemo } from 'react';
import { Plugin } from './components/Plugin';
import { AudioBackendContext } from './hooks/useAudioBackend';
import { AndroidBackend } from './backend/AndroidBackend';
import { MetersProvider } from './hooks/useMeters';
import { ErrorBoundary } from './components/ErrorBoundary';

function App() {
  // One backend for the app's lifetime; a fresh instance per render would
  // change the context value and re-render the whole tree every time.
  const backend = useMemo(() => new AndroidBackend(), []);

  return (
    <ErrorBoundary>
      <AudioBackendContext.Provider value={backend}>
        {/* Single shared meter poll loop for every meter in the UI. */}
        <MetersProvider>
          <Plugin />
        </MetersProvider>
      </AudioBackendContext.Provider>
    </ErrorBoundary>
  );
}

export default App;
