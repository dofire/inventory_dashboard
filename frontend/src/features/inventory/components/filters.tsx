import { useEffect, useRef, useState } from 'react'
import { RotateCcw } from 'lucide-react'
import { Button } from '../../../components/ui/button'
import type { InventoryFilters } from '../types'

export function Filters({
  filters,
  makes,
  disabled,
  onChange,
}: {
  filters: InventoryFilters
  makes: Record<string, string[]>
  disabled: boolean
  onChange: (patch: Partial<InventoryFilters>, reset?: boolean) => void
}) {
  const [age, setAge] = useState(filters.age)
  const ageTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const latestChange = useRef(onChange)
  latestChange.current = onChange
  const cancelAgeUpdate = () => {
    if (ageTimer.current) clearTimeout(ageTimer.current)
  }
  useEffect(() => {
    setAge(filters.age)
    return () => { if (ageTimer.current) clearTimeout(ageTimer.current) }
  }, [filters.age])
  const validAge = age === '' || (/^\d+$/.test(age) && Number(age) <= 2147483647)
  const applyAge = () => {
    cancelAgeUpdate()
    if (validAge && age !== filters.age) onChange({ age: age === '' ? '' : String(Number(age)) })
  }
  const models = filters.make
    ? makes[filters.make] || []
    : []
  const active = Boolean(filters.make || filters.model || age || filters.aging)
  return (
    <form
      className="filter-bar"
      aria-label="Inventory filters"
      onSubmit={(event) => {
        event.preventDefault()
        applyAge()
      }}
    >
      <div className="filter-field"><label htmlFor="make">
        Make
      </label>
      <select
        id="make"
        value={filters.make}
        disabled={disabled}
        onChange={(e) => onChange({ make: e.target.value, model: '' })}
      >
        <option value="">All makes</option>
        {filters.make && !makes[filters.make] && <option>{filters.make}</option>}
        {Object.keys(makes)
          .sort()
          .map((make) => (
            <option key={make}>{make}</option>
          ))}
      </select>
      </div><div className="filter-field"><label htmlFor="model">
        Model
      </label>
      <select
        id="model"
        value={filters.model}
        disabled={disabled || !filters.make}
        onChange={(e) => onChange({ model: e.target.value })}
      >
        <option value="">{filters.make ? 'All models' : 'Select a make first'}</option>
        {filters.model && !models.includes(filters.model) && <option>{filters.model}</option>}
        {models.map((model) => (
          <option key={model}>{model}</option>
        ))}
      </select>
      </div><div className="filter-field"><label htmlFor="age">Minimum days in stock</label>
      <div className="age-filter">
        <input
          id="age"
          type="text"
          inputMode="numeric"
          autoComplete="off"
          placeholder="Any age"
          aria-describedby={validAge ? undefined : 'age-hint'}
          aria-invalid={!validAge}
          value={age}
          onBlur={applyAge}
          onChange={(event) => {
            const value = event.target.value
            setAge(value)
            cancelAgeUpdate()
            if (value === '' || (/^\d+$/.test(value) && Number(value) <= 2147483647)) {
              ageTimer.current = setTimeout(() => {
                latestChange.current({ age: value === '' ? '' : String(Number(value)) })
              }, 600)
            }
          }}
        />
        <span className="age-unit" aria-hidden="true">days</span>
      </div>
      <span id="age-hint" className="age-hint form-error">{!validAge && 'Enter a whole number from 0 to 2147483647'}</span>
      </div><label className="checkbox-label">
        <input
          type="checkbox"
          checked={filters.aging}
          onChange={(e) => onChange({ aging: e.target.checked })}
        />
        Aging only (&gt;90 days)
      </label>
      <Button
          variant="ghost"
          disabled={!active}
          type="button"
          size="sm"
          onClick={() => {
            cancelAgeUpdate()
            setAge('')
            onChange({}, true)
          }}
        >
          <RotateCcw size={14} />
          Reset
        </Button>
    </form>
  )
}
