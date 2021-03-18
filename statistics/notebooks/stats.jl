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
	using Plots
	using CSVFiles
	using DataFrames
end

# ╔═╡ 32417062-881c-11eb-0179-f99feb1bbd6d


# ╔═╡ Cell order:
# ╟─8fca97a6-881a-11eb-318c-7756eb127ab6
# ╠═790a9962-881a-11eb-2d1f-e34eabe10138
# ╠═72ea4f46-881a-11eb-0314-4343c5cad805
# ╠═32417062-881c-11eb-0179-f99feb1bbd6d
