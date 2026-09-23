import type {
  IAudioBackend,
  ParameterMap,
  ParameterType,
} from '../types/IAudioBackend';

type AndroidBridge = {
  getState(): string;
  [key: string]: (...args: any[]) => any;
};

declare global {
  interface Window {
    Tone3000Android?: AndroidBridge;
  }
}

const bridge = (): AndroidBridge | undefined => window.Tone3000Android;

export function isNativeFunctionRegistered(name: string): boolean {
  return typeof bridge()?.[name] === 'function';
}

function call(name: string, ...args: unknown[]): Promise<unknown> {
  try {
    const fn = bridge()?.[name];
    return fn ? Promise.resolve(fn(...args)) : Promise.resolve(undefined);
  } catch (error) {
    return Promise.reject(error);
  }
}

function db(value: unknown, fallback = 0): number {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function statDb(stats: string, key: string): number {
  const match = stats.match(new RegExp(`${key}=[^\\n]*?\\((-?\\d+(?:\\.\\d+)?) dBFS\\)`));
  const value = match ? Number(match[1]) : -60;
  return Number.isFinite(value) ? Math.max(-60, Math.min(0, value)) : -60;
}

function meterState(): any {
  const raw = bridge()?.getStats?.();
  const stats = typeof raw === 'string' ? raw : '';
  const input = statDb(stats, 'capturePeak');
  const output = statDb(stats, 'postEqPeak');
  const cpuMatch = stats.match(/CPU budget used\(avg\)=(-?\d+(?:\.\d+)?)%/);
  const cpu = cpuMatch ? Math.max(0, Number(cpuMatch[1])) : 0;
  return { input: [input, input], output: [output, output], blocks: {}, cpu, correlation: 1 };
}

function stateSnapshot(): any {
  try {
    return JSON.parse(bridge()?.getState?.() ?? '{}');
  } catch {
    return {};
  }
}

function chainState(): any {
  const source = stateSnapshot();
  const nams = Array.isArray(source.namChain) ? source.namChain : [];
  const blocks = nams.map((nam: any, index: number) => {
    const id = `nam-${Number(nam.chainIndex ?? index)}`;
    const bands = [
      [120, 'lowshelf'], [750, 'bell'], [4000, 'highshelf'],
      [1800, 'bell'], [3500, 'bell'], [8000, 'highshelf'],
    ].map(([freqHz, type], band) => ({
      type,
      freqHz,
      gainDb: db(nam[`eq${band === 0 ? 'Low' : band === 1 ? 'Mid' : band === 2 ? 'High' : `Band${band}` }Db`]),
      q: 0.8,
    }));
    return {
      blockId: id,
      kind: 'tone',
      tone: {
        id: Number(nam.modelId ?? index),
        title: nam.toneTitle || nam.modelName || `NAM ${index + 1}`,
        format: 'NAM',
        models: [], models_count: 1, a2_models_count: 1,
        downloads_count: 0, favorites_count: 0,
      },
      activeModelId: Number(nam.modelId ?? index),
      loaded: true, loadFailed: false, modelLoading: false, irLong: false,
      params: {
        enabled: !Boolean(nam.bypass),
        normalize: nam.normalize !== false,
        slimSize: nam.a2Full ? 1 : 0,
        inputGain: (db(nam.inGainDb) + 24) / 48,
        outputGain: (db(nam.gainDb) + 24) / 36,
        mix: Math.max(0, Math.min(1, db(nam.mix, 1))),
        eq: { enabled: true, pre: Boolean(nam.eqPre), bands },
      },
    };
  });
  const cabinetBands = Array.isArray(source.cabinetIrEq) ? source.cabinetIrEq : [];
  const cabinet = source.cabinetIrLoaded ? {
    blockId: 'cabinet-ir', kind: 'tone',
    tone: { id: -1, title: source.cabinetIrName || 'Cabinet IR', format: 'IR', models: [], models_count: 1, a2_models_count: 0, downloads_count: 0, favorites_count: 0 },
    activeModelId: -1, loaded: true, loadFailed: false, modelLoading: false, irLong: false,
    params: {
      enabled: !Boolean(source.cabinetIrBypass), normalize: false, slimSize: 0,
      inputGain: (db(source.cabinetIrInGain) + 24) / 48,
      outputGain: (db(source.cabinetIrOutGain) + 24) / 36,
      mix: Math.max(0, Math.min(1, db(source.cabinetIrMix, 1))),
      eq: { enabled: true, pre: Boolean(source.cabinetIrEqPre), bands: [
        [120, 'lowshelf'], [750, 'bell'], [4000, 'highshelf'],
        [1800, 'bell'], [3500, 'bell'], [8000, 'highshelf'],
      ].map(([freqHz, type], band) => ({ type, freqHz, gainDb: db(cabinetBands[band]), q: 0.8 })) },
    },
  } : null;
  const ordered = cabinet ? (() => {
    const out: any[] = [];
    const pos = Number(source.cabinetIrPosition ?? nams.length);
    blocks.forEach((block: any, index: number) => {
      if (pos === index) out.push(cabinet);
      out.push(block);
    });
    if (pos >= blocks.length) out.push(cabinet);
    return out;
  })() : blocks;
  return {
    revision: Date.now(), canUndo: false, canRedo: false, atDefault: false,
    stereoEnabled: false, activeSide: 'left', stereoInput: false, stereoOutput: true,
    standalone: true, inputMode: 'left', namSlimSizeDefault: 0, multiCore: false,
    sampleRate: 48000, chain: [...ordered, { blockId: 'insert-0', kind: 'insert' }],
  };
}

function parameter<T extends ParameterType>(type: T): ParameterMap[T] {
  let value: any = type === 'toggle' ? false : 0;
  const listeners = new Map<number, (v: any) => void>();
  let next = 1;
  const common = {
    getValue: () => value,
    setValue: (v: any) => { value = v; listeners.forEach((fn) => fn(value)); },
    valueChangedEvent: { addListener: (fn: (v: any) => void) => { const id = next++; listeners.set(id, fn); return id; }, removeListener: (id: number) => listeners.delete(id) },
  };
  return common as ParameterMap[T];
}

export class AndroidBackend implements IAudioBackend {
  getParameterState<T extends ParameterType>(_name: string, type: T): ParameterMap[T] {
    return parameter(type);
  }

  getPluginFunction(name: string): (...args: unknown[]) => Promise<unknown> {
    if (name === 'getChainState') return async () => chainState();
    if (name === 'getMeterLevels') return async () => meterState();
    if (name === 'getPresetList') return async () => {
      const presets = Array.isArray(stateSnapshot().presets) ? stateSnapshot().presets : [];
      return {
        presets: presets.map((preset: any) => ({
          id: String(preset.slot),
          name: String(preset.label || `Preset ${preset.slot}`),
          saved: Boolean(preset.saved),
        })),
      };
    };
    if (name === 'loadPreset') return async (id) => {
      const slot = Number(String(id).replace(/^slot-/, ''));
      return Number.isFinite(slot) ? call('loadPreset', slot) : false;
    };
    if (name === 'savePreset') return async (nameArg) => {
      const presets = Array.isArray(stateSnapshot().presets) ? stateSnapshot().presets : [];
      const empty = presets.find((preset: any) => !preset.saved);
      const slot = Number(empty?.slot || 1);
      await call('savePreset', slot);
      return { id: String(slot), name: String(nameArg || `Preset ${slot}`) };
    };
    if (name === 'loadLocalTone') return async (title, files, targetBlockId) => {
      const response = await call('loadLocalTone', String(title), JSON.stringify(files ?? []), String(targetBlockId ?? ''));
      if (typeof response !== 'string') return response;
      try { return JSON.parse(response); } catch { return { error: response }; }
    };
    if (name === 'loadTone') return async (toneJson, targetInsertId) =>
      call('loadTone', String(toneJson), String(targetInsertId ?? ''));
    if (name === 'swapTone') return async (blockId, toneJson) =>
      Boolean(await call('loadTone', String(toneJson), String(blockId)));
    if (name === 'switchModel') return async (blockId, modelId, modelJson) => {
      const block = chainState().chain?.find((item: any) => item.blockId === String(blockId));
      let model: any;
      try { model = typeof modelJson === 'string' ? JSON.parse(modelJson) : modelJson; } catch { return false; }
      const tone = block?.tone ?? { id: modelId, title: 'NAM' };
      const payload = JSON.stringify({ id: tone.id, title: tone.title, models: [{ ...model, id: modelId }] });
      return Boolean(await call('loadTone', payload, String(blockId)));
    };
    if (name === 'setBlockParam') return async (blockId, param, value) => {
      if (String(blockId) === 'cabinet-ir') {
        if (param === 'inputGain') return call('setCabinetInGain', db(value) * 48 - 24);
        if (param === 'outputGain') return call('setCabinetOutGain', db(value) * 36 - 24);
        if (param === 'mix') return call('setCabinetMix', value);
        if (param === 'enabled') return call('setCabinetBypass', !Boolean(value));
        return Promise.resolve();
      }
      const index = Number(String(blockId).replace('nam-', ''));
      if (param === 'inputGain') return call('setNamInGain', index, db(value) * 48 - 24);
      if (param === 'outputGain') return call('setNamGain', index, db(value) * 36 - 24);
      if (param === 'mix') return call('setNamMix', index, value);
      if (param === 'enabled') return call('setNamBypass', index, !Boolean(value));
      if (param === 'normalize') return call('setNamNormalize', index, Boolean(value));
      return Promise.resolve();
    };
    if (name === 'setBlockEqBand') return async (blockId, band, value) => {
      const id = String(blockId);
      const gain = typeof value === 'object' && value !== null ? (value as any).gainDb : value;
      if (id === 'cabinet-ir') return call('setCabinetEq', Number(band), gain);
      return call('setNamEq', Number(id.replace('nam-', '')), Number(band), gain);
    };
    if (name === 'setBlockEqPre') return async (blockId, pre) => {
      if (String(blockId) === 'cabinet-ir') return call('setCabinetEqPosition', pre);
      return call('setNamEqPosition', Number(String(blockId).replace('nam-', '')), pre);
    };
    if (name === 'setBlockEqEnabled') return async (blockId, enabled) =>
      String(blockId) === 'cabinet-ir'
        ? call('setCabinetEqEnabled', Boolean(enabled))
        : call('setNamEqEnabled', Number(String(blockId).replace('nam-', '')), Boolean(enabled));
    if (name === 'resetBlockEq') return async (blockId) => {
      const id = String(blockId);
      for (let band = 0; band < 6; band++) {
        if (id === 'cabinet-ir') await call('setCabinetEq', band, 0);
        else await call('setNamEq', Number(id.replace('nam-', '')), band, 0);
      }
    };
    if (name === 'setBlockSlimSize') return async (blockId, size) => call('setNamQuality', Number(String(blockId).replace('nam-', '')), Number(size) >= 0.5);
    if (name === 'removeChainBlock') return async (blockId) => String(blockId) === 'cabinet-ir'
      ? call('removeCabinetIr')
      : call('removeNam', Number(String(blockId).replace('nam-', '')));
    if (name === 'reorderChainBlocks') return async (ids) =>
      call('reorderChain', JSON.stringify(ids ?? []));
    if (name === 'setStereoMode' || name === 'setInputMode' || name === 'setMultiCore') return async () => undefined;
    return (...args: unknown[]) => call(name, ...args);
  }

  addEventListener(_eventId: string, _fn: (payload: unknown) => void): () => void { return () => undefined; }
}
