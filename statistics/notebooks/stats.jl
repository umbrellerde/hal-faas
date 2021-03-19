### A Pluto.jl notebook ###
# v0.12.21

using Markdown
using InteractiveUtils

# ╔═╡ 8fca97a6-881a-11eb-318c-7756eb127ab6
md"""
Set up the environment, import packages
"""

# ╔═╡ 790a9962-881a-11eb-2d1f-e34eabe10138
begin
	import Pkg
	Pkg.activate(mktempdir())
end

# ╔═╡ 72ea4f46-881a-11eb-0314-4343c5cad805
begin
	Pkg.add("Plots")
	Pkg.add("CSVFiles")
	Pkg.add("DataFrames")
	Pkg.add("CSV")
	Pkg.add("JSON")
	using Plots
	using CSVFiles
	using DataFrames
	using CSV
	using JSON
end

# ╔═╡ 32417062-881c-11eb-0179-f99feb1bbd6d
begin
	df = CSV.read("test.csv", DataFrame)
	min_val = minimum(df.start)
	df.start .-= min_val
	df.end .-= min_val
	df.start_computation .-= min_val
	df.end_computation .-= min_val
	df
end

# ╔═╡ a1ec7962-8828-11eb-1a56-9f2cc3b1dc6e
test = JSON.parse.(replace.(df.result, "'" => "\""))

# ╔═╡ 9c0ac0b6-882d-11eb-2671-f512931e4754
test[1]["pid"]

# ╔═╡ ea2b1ce0-8827-11eb-1977-d909dbe36c77
begin
	plot(df.start, df.start_computation, label="start")
	plot!(df.start, df.end_computation, label="end")
end

# ╔═╡ cd94e8ba-882d-11eb-0e46-3bc956da6f7b
scatter(df.start, [el["pid"] for el in test])

# ╔═╡ Cell order:
# ╟─8fca97a6-881a-11eb-318c-7756eb127ab6
# ╠═790a9962-881a-11eb-2d1f-e34eabe10138
# ╠═72ea4f46-881a-11eb-0314-4343c5cad805
# ╠═32417062-881c-11eb-0179-f99feb1bbd6d
# ╠═a1ec7962-8828-11eb-1a56-9f2cc3b1dc6e
# ╠═9c0ac0b6-882d-11eb-2671-f512931e4754
# ╠═ea2b1ce0-8827-11eb-1977-d909dbe36c77
# ╠═cd94e8ba-882d-11eb-0e46-3bc956da6f7b
