export type CategoriaTipo = {
  categoria: string;
  tipos: { valor: string; descripcion: string }[];
};

export const CATALOGO_TIPOS: CategoriaTipo[] = [
  {
    categoria: 'Tipos Primitivos',
    tipos: [
      { valor: 'int', descripcion: 'Entero de 32 bits (int)' },
      { valor: 'short', descripcion: 'Entero corto de 16 bits (short)' },
      { valor: 'long', descripcion: 'Entero largo de 64 bits (long)' },
      { valor: 'float', descripcion: 'Flotante simple precisión (float)' },
      { valor: 'double', descripcion: 'Flotante doble precisión (double)' },
      { valor: 'boolean', descripcion: 'Booleano (boolean)' },
      { valor: 'char', descripcion: 'Carácter único (char)' },
      { valor: 'byte', descripcion: 'Byte de 8 bits (byte)' },
    ],
  },
  {
    categoria: 'Objetos y Wrappers',
    tipos: [
      { valor: 'String', descripcion: 'Cadena de texto (String / str)' },
      { valor: 'Integer', descripcion: 'Entero empaquetado (Integer)' },
      { valor: 'Long', descripcion: 'Entero largo empaquetado (Long)' },
      { valor: 'Float', descripcion: 'Flotante empaquetado (Float)' },
      { valor: 'Double', descripcion: 'Doble precisión empaquetado (Double)' },
      { valor: 'Boolean', descripcion: 'Booleano empaquetado (Boolean)' },
      { valor: 'Character', descripcion: 'Carácter empaquetado (Character)' },
      { valor: 'Byte', descripcion: 'Byte empaquetado (Byte)' },
    ],
  },
  {
    categoria: 'Fechas y Tipos Especiales',
    tipos: [
      { valor: 'Date', descripcion: 'Fecha estándar (Date)' },
      { valor: 'LocalDate', descripcion: 'Fecha sin hora (AAAA-MM-DD)' },
      { valor: 'LocalDateTime', descripcion: 'Fecha y hora (LocalDateTime)' },
      { valor: 'Instant', descripcion: 'Instante UTC (Instant)' },
      { valor: 'UUID', descripcion: 'Identificador universal único (UUID)' },
      { valor: 'BigDecimal', descripcion: 'Moneda / alta precisión (BigDecimal)' },
      { valor: 'byte[]', descripcion: 'Arreglo binario (byte[])' },
    ],
  },
];

export const TODOS_LOS_TIPOS_BASICOS = CATALOGO_TIPOS.flatMap((c) => c.tipos.map((t) => t.valor));

/**
 * Retorna exclusivamente el catálogo de tipos de datos puros.
 * No se incluyen clases ni tablas del modelo para mantener los atributos con tipos de datos estándar.
 */
export function obtenerTiposDisponibles(): CategoriaTipo[] {
  return CATALOGO_TIPOS;
}
